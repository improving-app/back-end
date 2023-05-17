package com.improving.app.organization.domain

import TestData.{baseAddress, baseOrganizationInfo}
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.organization.domain.Organization.OrganizationRequestEnvelope
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.Random

object OrganizationSpec {
  val config: Config = ConfigFactory.parseString("""
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on

    akka.actor.serialization-bindings{
      "com.improving.app.common.serialize.PBMsgSerializable" = proto
    }
  """)
}
class OrganizationSpec
    extends ScalaTestWithActorTestKit(OrganizationSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers {
  override def afterAll(): Unit = testKit.shutdownTestKit()

  def createTestVariables()
      : (String, ActorRef[OrganizationRequestEnvelope], TestProbe[StatusReply[OrganizationResponse]]) = {
    val organizationId = Random.nextString(31)
    val p = this.testKit.spawn(Organization(PersistenceId.ofUniqueId(organizationId)))
    val probe = this.testKit.createTestProbe[StatusReply[OrganizationResponse]]()
    (organizationId, p, probe)
  }

  def establishOrganization(
      organizationId: String,
      p: ActorRef[OrganizationRequestEnvelope],
      probe: TestProbe[StatusReply[OrganizationResponse]],
      organizationInfo: OrganizationInfo = baseOrganizationInfo
  ): Unit = {
    p ! Organization.OrganizationRequestEnvelope(
      EstablishOrganization(
        organizationId = OrganizationId(organizationId),
        onBehalfOf = MemberId("establishingUser"),
        organizationInfo = organizationInfo
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    assert(response.isSuccess)
  }

  private def suspendOrganization(
      organizationId: String,
      p: ActorRef[OrganizationRequestEnvelope],
      probe: TestProbe[StatusReply[OrganizationResponse]]
  ) = {
    p ! Organization.OrganizationRequestEnvelope(
      SuspendOrganization(
        organizationId = OrganizationId(organizationId),
        onBehalfOf = MemberId("suspendingUser"),
      ),
      probe.ref
    )

    val suspendOrganizationResponse = probe.receiveMessage()
    assert(suspendOrganizationResponse.isSuccess)
  }

  private def activateOrganization(
      organizationId: String,
      p: ActorRef[OrganizationRequestEnvelope],
      probe: TestProbe[StatusReply[OrganizationResponse]]
  ) = {
    p ! Organization.OrganizationRequestEnvelope(
      ActivateOrganization(
        organizationId = OrganizationId(organizationId),
        onBehalfOf = MemberId("activatingUser")
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    assert(response.isSuccess)
  }

  "A Organization Actor" when {
    // Should this also error out when other organizations have the same name?
    "in the Uninitialized State" when {
      "executing EstablishOrganization command" should {
        "error for an unauthorized updating user" ignore {
          val (organizationId, p, probe) = createTestVariables()

          p ! Organization.OrganizationRequestEnvelope(
            EstablishOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("unauthorizedUser"),
              organizationInfo = baseOrganizationInfo
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Organization"

        }

        "succeed for the golden path and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          p ! Organization.OrganizationRequestEnvelope(
            EstablishOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("establishingUser"),
              organizationInfo = baseOrganizationInfo
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationEstablished]
          successVal.organizationId shouldBe OrganizationId(
            organizationId
          )

          successVal.metaInfo.createdBy shouldBe MemberId(
            "establishingUser"
          )

        }

        "error for a organization that is already established" in {
          val (organizationId, p, probe) = createTestVariables()

          p ! Organization.OrganizationRequestEnvelope(
            EstablishOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("establishingUser"),
              organizationInfo = baseOrganizationInfo
            ),
            probe.ref
          )

          val establishResponse = probe.receiveMessage()
          assert(establishResponse.isSuccess)

          p ! Organization.OrganizationRequestEnvelope(
            EstablishOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("establishingUser"),
              organizationInfo = baseOrganizationInfo
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Message type not supported in draft state"
        }
      }
      "executing any command or query other than establish" should {
        "error as not established" in {
          val (organizationId, p, probe) = createTestVariables()

          val commands: Seq[OrganizationRequestPB] = Seq(
            ActivateOrganization(OrganizationId(organizationId), MemberId("user")),
            SuspendOrganization(OrganizationId(organizationId), MemberId("user")),
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("user"),
              EditableOrganizationInfo()
            ),
            AddMembersToOrganization(OrganizationId(organizationId), MemberId("user"), Seq(MemberId("member"))),
            AddOwnersToOrganization(OrganizationId(organizationId), MemberId("user"), Seq(MemberId("owner"))),
            RemoveMembersFromOrganization(OrganizationId(organizationId), MemberId("user"), Seq(MemberId("member"))),
            RemoveOwnersFromOrganization(OrganizationId(organizationId), MemberId("user"), Seq(MemberId("owner"))),
            GetOrganizationInfo(OrganizationId(organizationId), MemberId("user"))
          )

          commands.foreach(command => {
            p ! Organization.OrganizationRequestEnvelope(command, probe.ref)

            val response = probe.receiveMessage()

            assert(response.isError)

            val responseError = response.getError
            responseError.getMessage shouldEqual "Organization is not established"
          })
        }
      }
    }

    "in the Draft state" when {
      "executing EditOrganizationInfo command" should {
        "error for an unauthorized updating user" ignore {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          // Test command in question
          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("unauthorizedUser"),
              EditableOrganizationInfo(),
            ),
            probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Organization"
        }

        "succeed for an empty edit and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              EditableOrganizationInfo(),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe baseOrganizationInfo
          successVal.newInfo shouldBe baseOrganizationInfo
        }

        "succeed for an edit of all fields and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          val newAddress = baseAddress.copy(city = "Timbuktu")
          val newName = "A new name"

          val updateInfo = EditableOrganizationInfo(
            name = Some(newName),
            address = Some(newAddress),
          )

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              updateInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          val updatedInfo = baseOrganizationInfo.copy(name = newName, address = Some(newAddress))

          successVal.oldInfo shouldBe baseOrganizationInfo
          successVal.newInfo shouldBe updatedInfo
        }

        "succeed for a partial edit and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          val newName = "A new name"

          val updatedInfo = EditableOrganizationInfo(
            name = Some(newName)
          )

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              updatedInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe baseOrganizationInfo
          successVal.newInfo shouldBe baseOrganizationInfo.copy(name = newName)
        }
      }

      "executing GetOrganizationInfo query" should {
        "return the correct organization info for a new organization" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            GetOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val infoResponse = response.getValue.asInstanceOf[OrganizationInfoResponse]
          infoResponse.organizationId.id shouldEqual (organizationId)
          infoResponse.info shouldEqual (baseOrganizationInfo)
        }

        "return the correct organization info for an edited organization" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          val newAddress = baseAddress.copy(city = "Timbuktu")
          val newName = "A new name"

          val updateInfo = EditableOrganizationInfo(
            name = Some(newName),
            address = Some(newAddress),
          )

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              updateInfo,
            ),
            probe.ref
          )

          val editResponse = probe.receiveMessage()
          assert(editResponse.isSuccess)

          p ! Organization.OrganizationRequestEnvelope(
            GetOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val infoResponse = response.getValue.asInstanceOf[OrganizationInfoResponse]
          infoResponse.organizationId.id shouldEqual organizationId
          infoResponse.info.name shouldEqual newName
          infoResponse.info.address shouldEqual Some(newAddress)
        }
      }

      "executing the AddOwnersToOrganization command" should {
        "succeed for the golden path" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          val ownersToAdd = Seq(
            MemberId("owner1"),
            MemberId("owner2"),
            MemberId("owner3"),
          )

          p ! Organization.OrganizationRequestEnvelope(
            AddOwnersToOrganization(
              OrganizationId(organizationId),
              MemberId("updatingUser"),
              ownersToAdd
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isSuccess)

          val event1 = response1.getValue.asInstanceOf[OwnersAddedToOrganization]
          event1.ownersAdded shouldEqual ownersToAdd

          val nextOwnersToAdd = Seq(
            MemberId("owner2"),
            MemberId("owner3"),
            MemberId("owner4"),
          )

          p ! Organization.OrganizationRequestEnvelope(
            AddOwnersToOrganization(
              OrganizationId(organizationId),
              MemberId("updatingUser"),
              nextOwnersToAdd
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isSuccess)

          val event2 = response2.getValue.asInstanceOf[OwnersAddedToOrganization]
          event2.ownersAdded shouldEqual Seq(MemberId("owner4"))
        }
      }

      "executing the RemoveOwnersFromOrganization command" should {
        "succeed for the golden path" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          val ownersToAdd = Seq(
            MemberId("owner1"),
            MemberId("owner2"),
            MemberId("owner3"),
          )

          p ! Organization.OrganizationRequestEnvelope(
            AddOwnersToOrganization(
              OrganizationId(organizationId),
              MemberId("updatingUser"),
              ownersToAdd
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isSuccess)

          val event1 = response1.getValue.asInstanceOf[OwnersAddedToOrganization]
          event1.ownersAdded shouldEqual ownersToAdd

          val ownersToRemove = Seq(
            MemberId("owner2"),
            MemberId("owner3"),
            MemberId("owner4"),
          )

          p ! Organization.OrganizationRequestEnvelope(
            RemoveOwnersFromOrganization(
              OrganizationId(organizationId),
              MemberId("updatingUser"),
              ownersToRemove
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isSuccess)

          val event2 = response2.getValue.asInstanceOf[OwnersRemovedFromOrganization]
          event2.ownersRemoved shouldEqual Seq(MemberId("owner2"), MemberId("owner3"))
        }
      }

      "executing the AddMembersToOrganization command" should {
        "succeed for the golden path" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          val membersToAdd = Seq(
            MemberId("member1"),
            MemberId("member2"),
            MemberId("member3"),
          )

          p ! Organization.OrganizationRequestEnvelope(
            AddMembersToOrganization(
              OrganizationId(organizationId),
              MemberId("updatingUser"),
              membersToAdd
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isSuccess)

          val event1 = response1.getValue.asInstanceOf[MembersAddedToOrganization]
          event1.membersAdded shouldEqual membersToAdd

          val nextMembersToAdd = Seq(
            MemberId("member2"),
            MemberId("member3"),
            MemberId("member4"),
          )

          p ! Organization.OrganizationRequestEnvelope(
            AddMembersToOrganization(
              OrganizationId(organizationId),
              MemberId("updatingUser"),
              nextMembersToAdd
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isSuccess)

          val event2 = response2.getValue.asInstanceOf[MembersAddedToOrganization]
          event2.membersAdded shouldEqual Seq(MemberId("member4"))
        }
      }
    }

    "executing the RemoveMembersFromOrganization command" should {
      "succeed for the golden path" in {
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)

        val membersToAdd = Seq(
          MemberId("member1"),
          MemberId("member2"),
          MemberId("member3"),
        )

        p ! Organization.OrganizationRequestEnvelope(
          AddMembersToOrganization(
            OrganizationId(organizationId),
            MemberId("updatingUser"),
            membersToAdd
          ),
          probe.ref
        )

        val response1 = probe.receiveMessage()
        assert(response1.isSuccess)

        val event1 = response1.getValue.asInstanceOf[MembersAddedToOrganization]
        event1.membersAdded shouldEqual membersToAdd

        val membersToRemove = Seq(
          MemberId("member2"),
          MemberId("member3"),
          MemberId("member4"),
        )

        p ! Organization.OrganizationRequestEnvelope(
          RemoveMembersFromOrganization(
            OrganizationId(organizationId),
            MemberId("updatingUser"),
            membersToRemove
          ),
          probe.ref
        )

        val response2 = probe.receiveMessage()
        assert(response2.isSuccess)

        val event2 = response2.getValue.asInstanceOf[MembersRemovedFromOrganization]
        event2.membersRemoved shouldEqual Seq(MemberId("member2"), MemberId("member3"))
      }
    }

    "in the Active state" when {
      "executing ActivateOrganization command" should {
        "error and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            ActivateOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("activatingUser")
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "Message type not supported in active state"
        }
      }

      "executing SuspendOrganization command" should {
        "error for an unauthorized updating user" ignore {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            SuspendOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("unauthorizedUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Organization"
        }

        "succeed and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            SuspendOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val organizationSuspended = response.getValue.asInstanceOf[OrganizationSuspended]

          organizationSuspended.organizationId shouldEqual OrganizationId(organizationId)

          val organizationSuspendedMeta = organizationSuspended.metaInfo

          organizationSuspendedMeta.createdBy shouldEqual MemberId("establishingUser")
          organizationSuspendedMeta.lastUpdatedBy shouldEqual MemberId("suspendingUser")
        }
      }

      "executing EditOrganizationInfo command" should {
        "error for an unauthorized updating user" ignore {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          // Test command in question
          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("unauthorizedUser"),
              EditableOrganizationInfo(),
            ),
            probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Organization"
        }

        "succeed for an empty edit and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              EditableOrganizationInfo(),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe baseOrganizationInfo
          successVal.newInfo shouldBe baseOrganizationInfo
        }

        "succeed for an edit of all fields and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          val newAddress = baseAddress.copy(city = "Timbuktu")
          val newName = "A new name"

          val updateInfo = EditableOrganizationInfo(
            name = Some(newName),
            address = Some(newAddress),
          )

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              updateInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          val updatedInfo = baseOrganizationInfo.copy(name = newName, address = Some(newAddress))

          successVal.oldInfo shouldBe baseOrganizationInfo
          successVal.newInfo shouldBe updatedInfo
        }

        "succeed for a partial edit and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          activateOrganization(organizationId, p, probe)

          val newName = "A new name"

          val updatedInfo = EditableOrganizationInfo(
            name = Some(newName)
          )

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              updatedInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe baseOrganizationInfo
          successVal.newInfo shouldBe baseOrganizationInfo.copy(name = newName)
        }
      }

      "executing GetOrganizationInfo query" should {
        "return the correct organization info for a new organization" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            GetOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val infoResponse = response.getValue.asInstanceOf[OrganizationInfoResponse]
          infoResponse.organizationId.id shouldEqual (organizationId)
          infoResponse.info shouldEqual (baseOrganizationInfo)
        }

        "return the correct organization info for an edited organization" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          val newAddress = baseAddress.copy(city = "Timbuktu")
          val newName = "A new name"

          val updateInfo = EditableOrganizationInfo(
            name = Some(newName),
            address = Some(newAddress),
          )

          p ! Organization.OrganizationRequestEnvelope(
            EditOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
              updateInfo,
            ),
            probe.ref
          )

          val editResponse = probe.receiveMessage()
          assert(editResponse.isSuccess)

          p ! Organization.OrganizationRequestEnvelope(
            GetOrganizationInfo(
              OrganizationId(organizationId),
              MemberId("someUser"),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val infoResponse = response.getValue.asInstanceOf[OrganizationInfoResponse]
          infoResponse.organizationId.id shouldEqual organizationId
          infoResponse.info.name shouldEqual newName
          infoResponse.info.address shouldEqual Some(newAddress)
        }
      }
    }

    "in the Suspended state" when {

      "executing ActivateOrganization command" should {
        "error for an unauthorized updating user" ignore {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          suspendOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            ActivateOrganization(
              organizationId = OrganizationId(organizationId),
              MemberId("unauthorizedUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Organization"
        }

        "succeed and return the proper response" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          suspendOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            ActivateOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("activatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val organizationActivated = response.getValue.asInstanceOf[OrganizationActivated]

          organizationActivated.organizationId shouldEqual OrganizationId(organizationId)

          val organizationSuspendedMeta = organizationActivated.metaInfo

          organizationSuspendedMeta.createdBy shouldEqual MemberId("establishingUser")
          organizationSuspendedMeta.lastUpdatedBy shouldEqual MemberId("activatingUser")
        }
      }

      "executing SuspendOrganization command" should {
        "yield a state error" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          suspendOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            SuspendOrganization(
              organizationId = OrganizationId(organizationId),
              onBehalfOf = MemberId("updatingUser1")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Message type not supported in suspended state"
        }
      }
    }
    "executing EditOrganizationInfo command" should {
      "error for an unauthorized updating user" ignore {
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)
        suspendOrganization(organizationId, p, probe)

        // Test command in question
        p ! Organization.OrganizationRequestEnvelope(
          EditOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("unauthorizedUser"),
            EditableOrganizationInfo(),
          ),
          probe.ref
        )
        val response2 = probe.receiveMessage()

        assert(response2.isError)

        val responseError = response2.getError
        responseError.getMessage shouldEqual "User is not authorized to modify Organization"
      }

      "succeed for an empty edit and return the proper response" in {
        // Transition to Active state
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)
        suspendOrganization(organizationId, p, probe)

        p ! Organization.OrganizationRequestEnvelope(
          EditOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("someUser"),
            EditableOrganizationInfo(),
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

        successVal.oldInfo shouldBe baseOrganizationInfo
        successVal.newInfo shouldBe baseOrganizationInfo
      }

      "succeed for an edit of all fields and return the proper response" in {
        // Transition to Active state
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)
        suspendOrganization(organizationId, p, probe)

        val newAddress = baseAddress.copy(city = "Timbuktu")
        val newName = "A new name"

        val updateInfo = EditableOrganizationInfo(
          name = Some(newName),
          address = Some(newAddress),
        )

        p ! Organization.OrganizationRequestEnvelope(
          EditOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("someUser"),
            updateInfo,
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

        val updatedInfo = baseOrganizationInfo.copy(name = newName, address = Some(newAddress))

        successVal.oldInfo shouldBe baseOrganizationInfo
        successVal.newInfo shouldBe updatedInfo
      }

      "succeed for a partial edit and return the proper response" in {
        // Transition to Active state
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)
        suspendOrganization(organizationId, p, probe)

        val newName = "A new name"

        val updatedInfo = EditableOrganizationInfo(
          name = Some(newName)
        )

        p ! Organization.OrganizationRequestEnvelope(
          EditOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("someUser"),
            updatedInfo,
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

        successVal.oldInfo shouldBe baseOrganizationInfo
        successVal.newInfo shouldBe baseOrganizationInfo.copy(name = newName)
      }
    }

    "executing GetOrganizationInfo query" should {
      "return the correct organization info for a new organization" in {
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)

        p ! Organization.OrganizationRequestEnvelope(
          GetOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("someUser"),
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val infoResponse = response.getValue.asInstanceOf[OrganizationInfoResponse]
        infoResponse.organizationId.id shouldEqual (organizationId)
        infoResponse.info shouldEqual (baseOrganizationInfo)
      }

      "return the correct organization info for an edited organization" in {
        val (organizationId, p, probe) = createTestVariables()

        establishOrganization(organizationId, p, probe)
        val newAddress = baseAddress.copy(city = "Timbuktu")
        val newName = "A new name"

        val updateInfo = EditableOrganizationInfo(
          name = Some(newName),
          address = Some(newAddress),
        )

        p ! Organization.OrganizationRequestEnvelope(
          EditOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("someUser"),
            updateInfo,
          ),
          probe.ref
        )

        val editResponse = probe.receiveMessage()
        assert(editResponse.isSuccess)

        p ! Organization.OrganizationRequestEnvelope(
          GetOrganizationInfo(
            OrganizationId(organizationId),
            MemberId("someUser"),
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val infoResponse = response.getValue.asInstanceOf[OrganizationInfoResponse]
        infoResponse.organizationId.id shouldEqual organizationId
        infoResponse.info.name shouldEqual newName
        infoResponse.info.address shouldEqual Some(newAddress)
      }
    }
  }
}
