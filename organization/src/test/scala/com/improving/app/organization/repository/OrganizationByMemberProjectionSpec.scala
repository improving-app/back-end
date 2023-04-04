package com.improving.app.organization.repository

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.testkit.scaladsl.TestProjection
import akka.projection.testkit.scaladsl.TestSourceProvider
import akka.projection.testkit.scaladsl.ProjectionTestKit
import akka.stream.scaladsl.Source
import com.improving.app.common.domain.MemberId
import com.improving.app.organization._
import org.scalatest.wordspec.AnyWordSpecLike
import com.improving.app.organization.TestData._

class OrganizationByMemberProjectionSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  private val projectionTestKit = ProjectionTestKit(system)

  val repository = new TestOrganizationRepository
  val projectionId =
    ProjectionId("test-org-id", "organizations-0")

  private def createEnvelope(event: OrganizationEvent, seqNo: Long, timestamp: Long = 0L) =
    EventEnvelope(Offset.sequence(seqNo), "persistenceId", seqNo, event, timestamp)

  "The events from Organization" should {

    "organization by member projection - established organization" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - MembersAddedToOrganization" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              MembersAddedToOrganization(
                Some(testOrgId),
                testNewMembers,
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers ++ testNewMembers,
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - MembersRemovedFromOrganization" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              MembersRemovedFromOrganization(
                Some(testOrgId),
                testMembers,
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          Seq.empty[MemberId],
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - OwnersAddedToOrganization" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              OwnersAddedToOrganization(
                Some(testOrgId),
                testNewOwners,
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          testOwners ++ testNewOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - OwnersRemovedFromOrganization" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              OwnersRemovedFromOrganization(
                Some(testOrgId),
                testOwners,
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          Seq.empty[MemberId],
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - OrganizationInfoUpdated" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              OrganizationInfoEdited(
                Some(testOrgId),
                Some(testNewTestInfo),
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testNewTestInfo),
          Some(testNewParent),
          testMembers,
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - OrganizationActivated" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              OrganizationActivated(
                Some(testOrgId),
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - OrganizationSuspended" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              OrganizationSuspended(
                Some(testOrgId),
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - ParentUpdated" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              ParentUpdated(
                Some(testOrgId),
                Some(testNewParent),
                Some(testActingMember)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          testOwners,
          testContacts,
          Some(result.getMeta)
        )
      }
    }

    "organization by member projection - OrganizationContactsUpdated" in {

      val events =
        Source(
          List[EventEnvelope[OrganizationEvent]](
            createEnvelope(
              OrganizationEstablished(
                Some(testOrgId),
                Some(testInfo),
                Some(testNewParent),
                testMembers,
                testOwners,
                testContacts,
                Some(testActingMember)
              ),
              0L
            ),
            createEnvelope(
              OrganizationContactsUpdated(
                Some(testOrgId),
                testNewContacts,
                Some(testActingMember2)
              ),
              1L
            )
          )
        )

      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[OrganizationEvent]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[OrganizationEvent]](
          projectionId,
          sourceProvider,
          () => new OrganizationByMemberProjectionHandler("carts-0", system, repository)
        )

      projectionTestKit.run(projection) {
        val seq = repository.getOrganizationsByMemberByOrgId(testOrgId.id).futureValue
        seq.isEmpty shouldBe false

        val result = seq.head
        result shouldBe Organization(
          Some(testOrgId),
          Some(testInfo),
          Some(testNewParent),
          testMembers,
          testOwners,
          testNewContacts,
          Some(result.getMeta)
        )
      }
    }

  }
}
