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
      : (String, ActorRef[OrganizationRequestEnvelope], TestProbe[StatusReply[OrganizationEvent]]) = {
    val organizationId = Random.nextString(31)
    val p = this.testKit.spawn(Organization(PersistenceId.ofUniqueId(organizationId)))
    val probe = this.testKit.createTestProbe[StatusReply[OrganizationEvent]]()
    (organizationId, p, probe)
  }

  def establishOrganization(
      organizationId: String,
      p: ActorRef[OrganizationRequestEnvelope],
      probe: TestProbe[StatusReply[OrganizationEvent]],
      organizationInfo: OrganizationInfo = baseOrganizationInfo
  ): Unit = {
    p ! Organization.OrganizationRequestEnvelope(
      EstablishOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("establishingUser")),
        organizationInfo = Some(organizationInfo)
      ),
      probe.ref
    )

    val response = probe.receiveMessage()
    assert(response.isSuccess)
  }

  private def suspendOrganization(
      organizationId: String,
      p: ActorRef[OrganizationRequestEnvelope],
      probe: TestProbe[StatusReply[OrganizationEvent]]
  ) = {
    p ! Organization.OrganizationRequestEnvelope(
      SuspendOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("suspendingUser")),
      ),
      probe.ref
    )

    val suspendOrganizationResponse = probe.receiveMessage()
    assert(suspendOrganizationResponse.isSuccess)
  }

  private def activateOrganization(
      organizationId: String,
      p: ActorRef[OrganizationRequestEnvelope],
      probe: TestProbe[StatusReply[OrganizationEvent]]
  ) = {
    p ! Organization.OrganizationRequestEnvelope(
      ActivateOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("activatingUser"))
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
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("unauthorizedUser")),
              organizationInfo = Some(baseOrganizationInfo)
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
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("establishingUser")),
              organizationInfo = Some(baseOrganizationInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          successVal.asMessage.sealedValue.organizationEstablished.get.organizationId shouldBe Some(
            OrganizationId(organizationId)
          )
          successVal.asMessage.sealedValue.organizationEstablished.get.metaInfo.get.createdBy shouldBe Some(
            MemberId("establishingUser")
          )
        }

        "error for a organization that is already established" in {
          val (organizationId, p, probe) = createTestVariables()

          p ! Organization.OrganizationRequestEnvelope(
            EstablishOrganization(
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("establishingUser")),
              organizationInfo = Some(baseOrganizationInfo)
            ),
            probe.ref
          )

          val establishResponse = probe.receiveMessage()
          assert(establishResponse.isSuccess)

          p ! Organization.OrganizationRequestEnvelope(
            EstablishOrganization(
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("establishingUser")),
              organizationInfo = Some(baseOrganizationInfo)
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Message type not supported in draft state"
        }
      }
      "executing any command other than establish" should {
        "error as not established" in {
          val (organizationId, p, probe) = createTestVariables()

          val commands = Seq(
            ActivateOrganization(Some(OrganizationId(organizationId)), Some(MemberId("user"))),
            SuspendOrganization(Some(OrganizationId(organizationId)), Some(MemberId("user"))),
            EditOrganizationInfo(
              Some(OrganizationId(organizationId)),
              Some(MemberId("user")),
              Some(EditableOrganizationInfo())
            )
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("unauthorizedUser")),
              Some(EditableOrganizationInfo()),
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("someUser")),
              Some(EditableOrganizationInfo()),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe Some(baseOrganizationInfo)
          successVal.newInfo shouldBe Some(baseOrganizationInfo)
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("someUser")),
              Some(updateInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          val updatedInfo = baseOrganizationInfo.copy(name = newName, address = Some(newAddress))

          successVal.oldInfo shouldBe Some(baseOrganizationInfo)
          successVal.newInfo shouldBe Some(updatedInfo)
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe Some(baseOrganizationInfo)
          successVal.newInfo shouldBe Some(baseOrganizationInfo.copy(name = newName))
        }
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
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("activatingUser"))
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
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("unauthorizedUser"))
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
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.organizationSuspended.isDefined)

          val organizationSuspended = successVal.asMessage.sealedValue.organizationSuspended.get

          organizationSuspended.organizationId shouldEqual Some(OrganizationId(organizationId))

          assert(organizationSuspended.metaInfo.isDefined)

          val organizationSuspendedMeta = organizationSuspended.metaInfo.get

          organizationSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          organizationSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("suspendingUser"))
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("unauthorizedUser")),
              Some(EditableOrganizationInfo()),
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("someUser")),
              Some(EditableOrganizationInfo()),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe Some(baseOrganizationInfo)
          successVal.newInfo shouldBe Some(baseOrganizationInfo)
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("someUser")),
              Some(updateInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          val updatedInfo = baseOrganizationInfo.copy(name = newName, address = Some(newAddress))

          successVal.oldInfo shouldBe Some(baseOrganizationInfo)
          successVal.newInfo shouldBe Some(updatedInfo)
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
              Some(OrganizationId(organizationId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

          successVal.oldInfo shouldBe Some(baseOrganizationInfo)
          successVal.newInfo shouldBe Some(baseOrganizationInfo.copy(name = newName))
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
              organizationId = Some(OrganizationId(organizationId)),
              Some(MemberId("unauthorizedUser"))
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
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("activatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.organizationActivated.isDefined)

          val organizationActivated = successVal.asMessage.sealedValue.organizationActivated.get

          organizationActivated.organizationId shouldEqual Some(OrganizationId(organizationId))

          assert(organizationActivated.metaInfo.isDefined)

          val organizationSuspendedMeta = organizationActivated.metaInfo.get

          organizationSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          organizationSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("activatingUser"))
        }
      }

      "executing SuspendOrganization command" should {
        "yield a state error" in {
          val (organizationId, p, probe) = createTestVariables()

          establishOrganization(organizationId, p, probe)
          suspendOrganization(organizationId, p, probe)

          p ! Organization.OrganizationRequestEnvelope(
            SuspendOrganization(
              organizationId = Some(OrganizationId(organizationId)),
              onBehalfOf = Some(MemberId("updatingUser1"))
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
            Some(OrganizationId(organizationId)),
            Some(MemberId("unauthorizedUser")),
            Some(EditableOrganizationInfo()),
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
            Some(OrganizationId(organizationId)),
            Some(MemberId("someUser")),
            Some(EditableOrganizationInfo()),
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

        successVal.oldInfo shouldBe Some(baseOrganizationInfo)
        successVal.newInfo shouldBe Some(baseOrganizationInfo)
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
            Some(OrganizationId(organizationId)),
            Some(MemberId("someUser")),
            Some(updateInfo),
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

        val updatedInfo = baseOrganizationInfo.copy(name = newName, address = Some(newAddress))

        successVal.oldInfo shouldBe Some(baseOrganizationInfo)
        successVal.newInfo shouldBe Some(updatedInfo)
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
            Some(OrganizationId(organizationId)),
            Some(MemberId("someUser")),
            Some(updatedInfo),
          ),
          probe.ref
        )

        val response = probe.receiveMessage()
        assert(response.isSuccess)

        val successVal = response.getValue.asInstanceOf[OrganizationInfoEdited]

        successVal.oldInfo shouldBe Some(baseOrganizationInfo)
        successVal.newInfo shouldBe Some(baseOrganizationInfo.copy(name = newName))
      }
    }
  }
}
