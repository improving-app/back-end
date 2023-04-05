package com.improving.app.tenant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import com.improving.app.common.domain.{MemberId, OrganizationId, TenantId}
import com.improving.app.tenant.TestData._
import com.improving.app.tenant.domain.Tenant.TenantCommand
import com.improving.app.tenant.domain._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.Random

object TenantSpec {
  val config: Config = ConfigFactory.parseString("""
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on

    akka.actor.serialization-bindings{
      "com.improving.app.common.serialize.PBMsgSerializable" = proto
    }
  """)
}
class TenantSpec
    extends ScalaTestWithActorTestKit(TenantSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers {
  override def afterAll(): Unit = testKit.shutdownTestKit()

  def createTestVariables(): (String, ActorRef[TenantCommand], TestProbe[StatusReply[TenantEvent]]) = {
    val tenantId = Random.nextString(31)
    val p = this.testKit.spawn(Tenant(PersistenceId.ofUniqueId(tenantId)))
    val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()
    (tenantId, p, probe)
  }

  def establishTenant(
    tenantId: String,
    p: ActorRef[TenantCommand],
    probe: TestProbe[StatusReply[TenantEvent]],
    tenantInfo: TenantInfo = baseTenantInfo
  ): StatusReply[TenantEvent] = {
    p ! Tenant.TenantCommand(
      EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser")),
        tenantInfo = Some(tenantInfo)
      ),
      probe.ref
    )

    probe.receiveMessage()
  }

  "A Tenant Actor" when {
    // Should this also error out when other tenants have the same name?
    "in the Uninitialized State" when {
      "executing EstablishTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("unauthorizedUser")),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"

        }

        "succeed for the golden path and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("establishingUser")),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          successVal.asMessage.sealedValue.tenantEstablishedValue.get.tenantId shouldBe Some(TenantId(tenantId))
          successVal.asMessage.sealedValue.tenantEstablishedValue.get.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))
        }

        "error for a tenant that is already established" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("establishingUser")),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val establishResponse = probe.receiveMessage()
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("establishingUser")),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Tenant is already established"
        }
      }
      "executing any command other than establish" should {
        "error as not established" in {
          val (tenantId, p, probe) = createTestVariables()

          val commands = Seq(
            EditInfo(Some(TenantId(tenantId)), Some(MemberId("user")), Some(TenantInfo())),
            ActivateTenant(Some(TenantId(tenantId)), Some(MemberId("user"))),
            SuspendTenant(Some(TenantId(tenantId)), "just feel like it", Some(MemberId("user"))),
          )

          commands.foreach(command => {
            p ! Tenant.TenantCommand(command, probe.ref)

            val response = probe.receiveMessage()

            assert(response.isError)

            val responseError = response.getError
            responseError.getMessage shouldEqual "Tenant is not established"
          })
        }
      }
    }

    "in the Active state" when {
      "executing EditInfo command" should {
        "error for an unauthorized updating user" ignore {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("unauthorizedUser")),
              Some(TenantInfo()),
            ),
            probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed for an empty edit and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(TenantInfo()),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.infoEditedValue.isDefined)

          val infoEdited = successVal.asMessage.sealedValue.infoEditedValue.get

          infoEdited.oldInfo shouldBe Some(baseTenantInfo)
          infoEdited.newInfo shouldBe Some(baseTenantInfo)
        }

        "succeed for an edit of all fields and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          val newContact = baseContact.copy(firstName = "Bob")
          val newAddress = baseAddress.copy(city = "Timbuktu")
          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = TenantInfo(
            name = newName,
            primaryContact = Some(newContact),
            address = Some(newAddress),
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.infoEditedValue.isDefined)

          val infoEdited = successVal.asMessage.sealedValue.infoEditedValue.get

          infoEdited.oldInfo shouldBe Some(baseTenantInfo)
          infoEdited.newInfo shouldBe Some(updatedInfo)
        }

        "succeed for a partial edit and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = TenantInfo(
            name = newName,
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.infoEditedValue.isDefined)

          val infoEdited = successVal.asMessage.sealedValue.infoEditedValue.get

          infoEdited.oldInfo shouldBe Some(baseTenantInfo)
          infoEdited.newInfo shouldBe Some(baseTenantInfo.copy(name = newName, organizations = Some(newOrgs)))
        }

        "error for incomplete updating primary contact info" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          val badContact = baseContact.copy(emailAddress = None)

          val updateInfo = TenantInfo(primaryContact = Some(badContact))

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("updatingUser")),
              Some(updateInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Primary contact info is not complete"
        }

        "error for an incomplete address" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          val badAddress = baseAddress.copy(city = "")

          val updateInfo = TenantInfo(address = Some(badAddress))

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("updatingUser")),
              Some(updateInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"
        }

      }

      "executing ActiveTenant command" should {
        "error and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              activatingUser = Some(MemberId("activatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Active tenants may not transition to the Active state"
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              suspendingUser = Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantSuspendedValue.isDefined)

          val tenantSuspended = successVal.asMessage.sealedValue.tenantSuspendedValue.get

          tenantSuspended.tenantId shouldEqual Some(TenantId(tenantId))
          tenantSuspended.suspensionReason shouldEqual "reason1"

          assert(tenantSuspended.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantSuspended.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("suspendingUser"))
        }
      }
    }

    "in the Suspended state" when {

      "executing EditInfo command" should {
        "error for an unauthorized updating user" ignore {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("unauthorizedUser")),
              Some(TenantInfo()),
            ),
            probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed for an empty edit and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(TenantInfo()),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.infoEditedValue.isDefined)

          val infoEdited = successVal.asMessage.sealedValue.infoEditedValue.get

          assert(infoEdited.metaInfo.isDefined)
          assert(infoEdited.oldInfo.isDefined)
          infoEdited.oldInfo.get shouldEqual baseTenantInfo
          infoEdited.newInfo.get shouldEqual baseTenantInfo
        }

        "succeed for an edit of all fields and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          val newContact = baseContact.copy(firstName = "Bob")
          val newAddress = baseAddress.copy(city = "Timbuktu")
          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = TenantInfo(
            name = newName,
            primaryContact = Some(newContact),
            address = Some(newAddress),
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.infoEditedValue.isDefined)

          val infoEdited = successVal.asMessage.sealedValue.infoEditedValue.get

          assert(infoEdited.metaInfo.isDefined)
          assert(infoEdited.oldInfo.isDefined)
          infoEdited.oldInfo.get shouldEqual baseTenantInfo
          infoEdited.newInfo.get shouldEqual updatedInfo
        }

        "succeed for a partial edit and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = TenantInfo(
            name = newName,
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.infoEditedValue.isDefined)

          val infoEdited = successVal.asMessage.sealedValue.infoEditedValue.get

          assert(infoEdited.metaInfo.isDefined)
          assert(infoEdited.oldInfo.isDefined)
          infoEdited.oldInfo.get shouldEqual baseTenantInfo
          infoEdited.newInfo.get shouldEqual baseTenantInfo.copy(name = newName, organizations = Some(newOrgs))
        }

        "error for incomplete updating primary contact info" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          val badContact = baseContact.copy(emailAddress = None)

          val updateInfo = TenantInfo(primaryContact = Some(badContact))

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("updatingUser")),
              Some(updateInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Primary contact info is not complete"
        }

        "error for an incomplete address" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          val badAddress = baseAddress.copy(city = "")

          val updateInfo = TenantInfo(address = Some(badAddress))

          p ! Tenant.TenantCommand(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("updatingUser")),
              Some(updateInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"
        }

      }

      "executing ActiveTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspendingUser = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspendingUser = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

            // Change state to active
          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              activatingUser = Some(MemberId("activatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantActivatedValue.isDefined)

          val tenantActivated = successVal.asMessage.sealedValue.tenantActivatedValue.get

          tenantActivated.tenantId shouldEqual Some(TenantId(tenantId))

          assert(tenantActivated.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantActivated.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("activatingUser"))
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspendingUser = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              Some(MemberId("someUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              suspendingUser = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("updatingUser1"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantSuspendedValue.isDefined)

          val tenantSuspended = successVal.asMessage.sealedValue.tenantSuspendedValue.get

          tenantSuspended.tenantId shouldEqual Some(TenantId(tenantId))
          tenantSuspended.suspensionReason shouldEqual "reason1"

          assert(tenantSuspended.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantSuspended.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser1"))
        }
      }
    }
  }
}