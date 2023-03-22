package com.improving.app.organization.repository

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.cluster.MemberStatus
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.testkit.scaladsl.PersistenceInit
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.improving.app.organization._
import com.improving.app.organization.TestData._
import com.improving.app.organization.domain.Organization
import com.improving.app.organization.domain.Organization.OrganizationCommand
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.DoNotDiscover
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object OrganizationByMemberIntegrationSpec {

  private val uniqueQualifier = System.currentTimeMillis()
  private val keyspace =
    s"IntegrationSpec_$uniqueQualifier"

  val config: Config = ConfigFactory
    .parseString(s"""

      akka.persistence.journal.plugin =  "akka.persistence.cassandra.journal"
      akka.persistence.snapshot-store {
         plugin = "akka.persistence.cassandra.snapshot"
      }
      akka.persistence.cassandra {
        events-by-tag {
          eventual-consistency-delay = 200ms
        }

        query {
          refresh-interval = 500 ms
        }

        journal.keyspace = $keyspace
        journal.keyspace-autocreate = on
        journal.tables-autocreate = on
        snapshot.keyspace-autocreate = on
        snapshot.tables-autocreate = on
      }

      akka.projection.cassandra.offset-store.keyspace = $keyspace
    """)
    .withFallback(ConfigFactory.load())

}

@DoNotDiscover
class OrganizationByMemberIntegrationSpec
    extends ScalaTestWithActorTestKit(OrganizationByMemberIntegrationSpec.config)
    with AnyWordSpecLike {

  private val log = LoggerFactory.getLogger(this.getClass)

  private lazy val OrganizationByMemberRepository = {
    val session =
      CassandraSessionRegistry(system).sessionFor("akka.persistence.cassandra")
    val OrganizationByMemberKeyspace =
      system.settings.config
        .getString("akka.projection.cassandra.offset-store.keyspace")
    new OrganizationRepositoryImpl(session, OrganizationByMemberKeyspace)(system.executionContext)
  }

  override protected def beforeAll(): Unit = {
    val timeout = 10.seconds
    Await.result(PersistenceInit.initializeDefaultPlugins(system, timeout), timeout)
    CreateTableTestUtils.createTables(system)

    Organization.init(system)

    OrganizationByMemberProjection.init(system, OrganizationByMemberRepository)

    super.beforeAll()
  }

  "Organization By Member projection" should {
    "init and join Cluster" in {
      Cluster(system).manager ! Join(Cluster(system).selfMember.address)

      eventually {
        Cluster(system).selfMember.status should ===(MemberStatus.Up)
      }
    }

    "should establish organization and persist the result" in {
      val sharding = ClusterSharding(system)
      val orgId = testOrgId

      val org1 = sharding.entityRefFor(Organization.OrganizationEntityKey, orgId.id)

      val reply1: Future[OrganizationResponse] =
        org1.askWithStatus(
          OrganizationCommand(
            EstablishOrganizationRequest(
              Some(testInfo),
              Some(testNewParent),
              testMembers,
              testOwners,
              testContacts,
              Some(testActingMember)
            ),
            _
          )
        )

      reply1.futureValue.isEmpty shouldBe false

      val oid = reply1.futureValue match {
        case OrganizationEventResponse(OrganizationEstablished(Some(orgId), _, _, _, _, _, _, _), _) => orgId
        case other                                                                                   => throw new IllegalStateException(s"reply1 is null. other - $other")
      }

      log.info(s"oid - $oid")

      eventually {
        OrganizationByMemberRepository.getOrganizationsByMemberByOrgId(oid.id).futureValue.isEmpty shouldBe false
      }
      log.info(
        s"OrganizationByMemberRepository.getOrganizationsByMemberByOrgId(oid.id).futureValue ------------------- ${OrganizationByMemberRepository.getOrganizationsByMemberByOrgId(oid.id).futureValue}"
      )
      OrganizationByMemberRepository.updateOrganizationByMember(
        oid.id,
        "test-member-id1",
        com.improving.app.organization
          .Organization(Some(testOrgId), Some(testInfo), Some(testNewParent), testMembers, testOwners, testContacts)
      )
      OrganizationByMemberRepository.getOrganizationsByMemberByOrgId(oid.id).futureValue.isEmpty shouldBe false
    }
  }
}
