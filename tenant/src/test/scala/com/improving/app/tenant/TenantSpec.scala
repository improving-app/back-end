package com.improving.app.tenant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import com.improving.app.common.domain.{MemberId, OrganizationId, TenantId}
import com.improving.app.tenant.TestData.{baseAddress, baseContact, baseTenantInfo}
import com.improving.app.tenant.domain.Tenant.TenantCommand
import com.improving.app.tenant.domain._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import com.improving.app.tenant.domain.util.EditableInfoUtil

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

  def createTestVariables(): (String, ActorRef[TenantCommand], TestProbe[StatusReply[TenantEnvelope]]) = {
    val tenantId = Random.nextString(31)
    val p = this.testKit.spawn(Tenant(PersistenceId.ofUniqueId(tenantId)))
    val probe = this.testKit.createTestProbe[StatusReply[TenantEnvelope]]()
    (tenantId, p, probe)
  }

  def establishTenant(
      tenantId: String,
      p: ActorRef[TenantCommand],
      probe: TestProbe[StatusReply[TenantEnvelope]],
      tenantInfo: Option[EditableTenantInfo] = Some(baseTenantInfo)
  ): StatusReply[TenantEnvelope] = {
    p ! Tenant.TenantCommand(
      EstablishTenant(
        tenantId = TenantId(tenantId),
        establishingUser = MemberId("establishingUser"),
        tenantInfo = tenantInfo
      ),
      probe.ref
    )

    probe.receiveMessage()
  }

  def terminateTenant(
      tenantId: String,
      p: ActorRef[TenantCommand],
      probe: TestProbe[StatusReply[TenantEnvelope]]
  ): StatusReply[TenantEnvelope] = {
    val establishResponse = establishTenant(tenantId, p, probe)
    assert(establishResponse.isSuccess)

    p ! Tenant.TenantCommand(
      TerminateTenant(
        tenantId = TenantId(tenantId),
        terminatingUser = MemberId("terminatingUser")
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
              tenantId = TenantId(tenantId),
              establishingUser = MemberId("unauthorizedUser"),
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
              tenantId = TenantId(tenantId),
              establishingUser = MemberId("establishingUser"),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal =
            response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantEstablishedValue
          successVal.tenantId shouldBe TenantId(tenantId)
          successVal.metaInfo.createdBy shouldBe MemberId("establishingUser")
        }

        "error for a tenant that is already established" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = TenantId(tenantId),
              establishingUser = MemberId("establishingUser"),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val establishResponse = probe.receiveMessage()
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = TenantId(tenantId),
              establishingUser = MemberId("establishingUser"),
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
      "executing GetOrganizations command" should {
        "succeed but return an empty list" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            GetOrganizations(
              tenantId = TenantId(tenantId)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val responseVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          responseVal.organizations shouldBe TenantOrganizationList()
        }
      }
      "executing any command other than establish" should {
        "error as not established" in {
          val (tenantId, p, probe) = createTestVariables()

          val commands = Seq(
            EditInfo(TenantId(tenantId), MemberId("user"), EditableTenantInfo()),
            ActivateTenant(TenantId(tenantId), MemberId("user")),
            SuspendTenant(TenantId(tenantId), "just feel like it", MemberId("user")),
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

      "executing TerminateTenant command" should {
        "error as not established" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            TerminateTenant(
              tenantId = TenantId(tenantId),
              terminatingUser = MemberId("terminatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Tenant is not established"
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
              TenantId(tenantId),
              MemberId("unauthorizedUser"),
              EditableTenantInfo(),
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
              TenantId(tenantId),
              MemberId("someUser"),
              EditableTenantInfo(),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.getEditable shouldBe baseTenantInfo
          successVal.newInfo.getEditable shouldBe baseTenantInfo
        }

        "succeed for an edit of all fields and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          val newContact = baseContact.copy(firstName = Some("Bob"))
          val newAddress = baseAddress.copy(city = Some("Timbuktu"))
          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = EditableTenantInfo(
            name = Some(newName),
            primaryContact = Some(newContact),
            address = Some(newAddress),
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              updatedInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.getEditable shouldBe baseTenantInfo
          successVal.newInfo.getEditable shouldBe updatedInfo
        }

        "succeed for a partial edit and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = EditableTenantInfo(
            name = Some(newName),
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              updatedInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.getEditable shouldBe baseTenantInfo
          successVal.newInfo.getEditable shouldBe baseTenantInfo.copy(
            name = Some(newName),
            organizations = Some(newOrgs)
          )
        }
      }

      "executing ActiveTenant command" should {
        "error on incomplete info" in {
          val (tenantId, p, probe) = createTestVariables()
          establishTenant(tenantId, p, probe, None)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              TenantId(tenantId),
              MemberId("someUser"),
            ),
            probe.ref
          )

          val response: StatusReply[TenantEnvelope] = probe.receiveMessage()

          assert(response.isError)

          val responseError: Throwable = response.getError
          responseError.getMessage shouldEqual "No associated name"

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              EditableTenantInfo(name = Some(baseTenantInfo.getName))
            ),
            probe.ref
          )
          assert(probe.receiveMessage().isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              TenantId(tenantId),
              MemberId("someUser")
            ),
            probe.ref
          )

          val response2: StatusReply[TenantEnvelope] = probe.receiveMessage()

          assert(response2.isError)

          val responseError2: Throwable = response2.getError
          responseError2.getMessage shouldEqual "No associated primary contact"

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              EditableTenantInfo(primaryContact = Some(baseContact), address = Some(baseAddress))
            ),
            probe.ref
          )
          assert(probe.receiveMessage().isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              TenantId(tenantId),
              MemberId("someUser")
            ),
            probe.ref
          )

          val response3: StatusReply[TenantEnvelope] = probe.receiveMessage()

          assert(response3.isError)

          val responseError3: Throwable = response3.getError
          responseError3.getMessage shouldEqual "No associated organizations"
        }

        "error and return the proper response when Activating an already active tenant" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = TenantId(tenantId),
              activatingUser = MemberId("activatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = TenantId(tenantId),
              activatingUser = MemberId("activatingUser")
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
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
              tenantId = TenantId(tenantId),
              suspensionReason = "reason",
              suspendingUser = MemberId("unauthorizedUser")
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
              tenantId = TenantId(tenantId),
              suspensionReason = "reason1",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantSuspendedValue

          successVal.tenantId shouldEqual TenantId(tenantId)
          successVal.suspensionReason shouldEqual "reason1"

          val tenantSuspendedMeta = successVal.metaInfo

          tenantSuspendedMeta.createdBy shouldEqual MemberId("establishingUser")
          tenantSuspendedMeta.lastUpdatedBy shouldEqual MemberId("suspendingUser")
        }
      }

      "executing GetOrganizations command" should {
        "succeed and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(
            tenantId,
            p,
            probe,
            Some(
              baseTenantInfo.copy(organizations =
                Some(
                  TenantOrganizationList(
                    Seq(
                      OrganizationId("org1"),
                      OrganizationId("org2")
                    )
                  )
                )
              )
            )
          )
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            GetOrganizations(
              tenantId = TenantId(tenantId)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          successVal.organizations shouldEqual TenantOrganizationList(
            Seq(
              OrganizationId("org1"),
              OrganizationId("org2")
            )
          )
        }
      }

      "executing TerminateTenant command" should {
        "error for an unauthorized terminating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            TerminateTenant(
              tenantId = TenantId(tenantId),
              terminatingUser = MemberId("terminatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"

        }
        "succeed for the golden path" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            TerminateTenant(
              tenantId = TenantId(tenantId),
              terminatingUser = MemberId("terminatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal =
            response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantTerminatedValue
          successVal.tenantId.id shouldBe tenantId

          val metaInfo = successVal.metaInfo

          metaInfo.createdBy.id shouldBe "establishingUser"
          metaInfo.lastUpdatedBy.id shouldBe "terminatingUser"
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
              tenantId = TenantId(tenantId),
              suspensionReason = "reason1",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("unauthorizedUser"),
              EditableTenantInfo(),
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
              tenantId = TenantId(tenantId),
              suspensionReason = "reason1",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              EditableTenantInfo(),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.getInfo shouldEqual baseTenantInfo.toInfo
          successVal.newInfo.getInfo shouldEqual baseTenantInfo.toInfo
        }

        "succeed for an edit of all fields and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspensionReason = "reason1",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          val newContact = baseContact.copy(firstName = Some("Bob"))
          val newAddress = baseAddress.copy(city = Some("Timbuktu"))
          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = EditableTenantInfo(
            name = Some(newName),
            primaryContact = Some(newContact),
            address = Some(newAddress),
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              updatedInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.getInfo shouldEqual baseTenantInfo.toInfo
          successVal.newInfo.getInfo shouldEqual updatedInfo.toInfo
        }

        "succeed for a partial edit and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspensionReason = "reason1",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          val newName = "A new name"
          val newOrgs = TenantOrganizationList(Seq(OrganizationId("a"), OrganizationId("b")))

          val updatedInfo = EditableTenantInfo(
            name = Some(newName),
            organizations = Some(newOrgs)
          )

          p ! Tenant.TenantCommand(
            EditInfo(
              TenantId(tenantId),
              MemberId("someUser"),
              updatedInfo,
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.getInfo shouldEqual baseTenantInfo.toInfo
          successVal.newInfo.getInfo shouldEqual baseTenantInfo
            .copy(
              name = Some(newName),
              organizations = Some(newOrgs)
            )
            .toInfo
        }
      }

      "executing ActiveTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspendingUser = MemberId("suspendingUser"),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = TenantId(tenantId),
              MemberId("unauthorizedUser")
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
              tenantId = TenantId(tenantId),
              suspendingUser = MemberId("suspendingUser"),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          // Change state to active
          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = TenantId(tenantId),
              activatingUser = MemberId("activatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantActivatedValue

          successVal.tenantId shouldEqual TenantId(tenantId)

          val tenantSuspendedMeta = successVal.metaInfo

          tenantSuspendedMeta.createdBy shouldEqual MemberId("establishingUser")
          tenantSuspendedMeta.lastUpdatedBy shouldEqual MemberId("activatingUser")
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspendingUser = MemberId("suspendingUser"),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspensionReason = "reason",
              MemberId("someUser")
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
              tenantId = TenantId(tenantId),
              suspensionReason = "reason",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspensionReason = "reason1",
              suspendingUser = MemberId("updatingUser1")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantSuspendedValue

          successVal.tenantId shouldEqual TenantId(tenantId)
          successVal.suspensionReason shouldEqual "reason1"

          val tenantSuspendedMeta = successVal.metaInfo

          tenantSuspendedMeta.createdBy shouldEqual MemberId("establishingUser")
          tenantSuspendedMeta.lastUpdatedBy shouldEqual MemberId("updatingUser1")
        }
      }

      "executing GetOrganizations command" should {
        "succeed and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(
            tenantId,
            p,
            probe,
            Some(
              baseTenantInfo.copy(organizations =
                Some(
                  TenantOrganizationList(
                    Seq(
                      OrganizationId("org1"),
                      OrganizationId("org2")
                    )
                  )
                )
              )
            )
          )
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspendingUser = MemberId("suspendingUser"),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            GetOrganizations(
              tenantId = TenantId(tenantId)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          successVal.organizations shouldEqual
            TenantOrganizationList(
              Seq(
                OrganizationId("org1"),
                OrganizationId("org2")
              )
            )
        }
      }

      "executing TerminateTenant command" should {
        "succeed for the golden path" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspensionReason = "reason",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            TerminateTenant(
              tenantId = TenantId(tenantId),
              terminatingUser = MemberId("terminatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal =
            response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantTerminatedValue
          successVal.tenantId.id shouldBe tenantId

          val metaInfo = successVal.metaInfo

          metaInfo.createdBy.id shouldBe "establishingUser"
          metaInfo.lastUpdatedBy.id shouldBe "terminatingUser"
        }
      }
    }

    "in the Terminated state" when {
      "executing EditInfo command" should {
        "error as an action not permitted" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EditInfo(
              tenantId = TenantId(tenantId),
              editingUser = MemberId("editingUser"),
              infoToUpdate = EditableTenantInfo(
                baseTenantInfo.name,
                Some(baseContact),
                Some(baseAddress),
                baseTenantInfo.organizations
              )
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          response.getError.getMessage shouldEqual "Command not allowed in Terminated state"
        }
      }

      "executing ActiveTenant command" should {
        "error as an action not permitted" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = TenantId(tenantId),
              activatingUser = MemberId("activatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          response.getError.getMessage shouldEqual "Command not allowed in Terminated state"
        }
      }

      "executing SuspendTenant command" should {
        "error as an action not permitted" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = TenantId(tenantId),
              suspensionReason = "reason",
              suspendingUser = MemberId("suspendingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          response.getError.getMessage shouldEqual "Command not allowed in Terminated state"
        }
      }

      "executing GetOrganizations command" should {
        "succeed but return an empty list" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantCommand(
            GetOrganizations(
              tenantId = TenantId(tenantId)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val responseVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          responseVal.organizations shouldBe TenantOrganizationList()
        }
      }

      "executing TerminateTenant command" should {
        "error as an action not permitted" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantCommand(
            TerminateTenant(
              tenantId = TenantId(tenantId),
              terminatingUser = MemberId("terminatingUser")
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          response.getError.getMessage shouldEqual "Command not allowed in Terminated state"
        }
      }
    }
  }
}
