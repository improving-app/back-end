package com.improving.app.tenant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import com.improving.app.common.domain.{MemberId, OrganizationId, TenantId}
import com.improving.app.tenant.TestData.{baseAddress, baseContact, baseTenantInfo}
import com.improving.app.tenant.domain.Tenant.TenantRequestEnvelope
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

  def createTestVariables(): (String, ActorRef[TenantRequestEnvelope], TestProbe[StatusReply[TenantResponse]]) = {
    val tenantId = Random.nextString(31)
    val p = this.testKit.spawn(Tenant(PersistenceId.ofUniqueId(tenantId)))
    val probe = this.testKit.createTestProbe[StatusReply[TenantResponse]]()
    (tenantId, p, probe)
  }

  def establishTenant(
      tenantId: String,
      p: ActorRef[TenantRequestEnvelope],
      probe: TestProbe[StatusReply[TenantResponse]],
      tenantInfo: Option[EditableTenantInfo] = Some(baseTenantInfo)
  ): StatusReply[TenantResponse] = {
    p ! Tenant.TenantRequestEnvelope(
      EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        onBehalfOf = Some(MemberId("establishingUser")),
        tenantInfo = tenantInfo
      ),
      probe.ref
    )

    probe.receiveMessage()
  }

  def terminateTenant(
      tenantId: String,
      p: ActorRef[TenantRequestEnvelope],
      probe: TestProbe[StatusReply[TenantResponse]]
  ): StatusReply[TenantResponse] = {
    val establishResponse = establishTenant(tenantId, p, probe)
    assert(establishResponse.isSuccess)

    p ! Tenant.TenantRequestEnvelope(
      TerminateTenant(
        tenantId = Some(TenantId(tenantId)),
        onBehalfOf = Some(MemberId("terminatingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("unauthorizedUser")),
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

          p ! Tenant.TenantRequestEnvelope(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("establishingUser")),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal =
            response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantEstablishedValue
          successVal.tenantId shouldBe Some(TenantId(tenantId))
          successVal.getMetaInfo.createdBy shouldBe Some(MemberId("establishingUser"))
        }

        "error for a tenant that is already established" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantRequestEnvelope(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("establishingUser")),
              tenantInfo = Some(baseTenantInfo)
            ),
            probe.ref
          )

          val establishResponse = probe.receiveMessage()
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("establishingUser")),
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

          p ! Tenant.TenantRequestEnvelope(
            GetOrganizations(
              tenantId = Some(TenantId(tenantId))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val responseVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          responseVal.getOrganizations shouldBe TenantOrganizationList()
        }
      }
      "executing any command other than establish" should {
        "error as not established" in {
          val (tenantId, p, probe) = createTestVariables()

          val commands = Seq(
            EditInfo(Some(TenantId(tenantId)), Some(MemberId("user")), Some(EditableTenantInfo())),
            ActivateTenant(Some(TenantId(tenantId)), Some(MemberId("user"))),
            SuspendTenant(Some(TenantId(tenantId)), "just feel like it", Some(MemberId("user"))),
          )

          commands.foreach(command => {
            p ! Tenant.TenantRequestEnvelope(command, probe.ref)

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

          p ! Tenant.TenantRequestEnvelope(
            TerminateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("terminatingUser"))
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
          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("unauthorizedUser")),
              Some(EditableTenantInfo()),
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

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(EditableTenantInfo()),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.map(_.getEditable) shouldBe Some(baseTenantInfo)
          successVal.newInfo.map(_.getEditable) shouldBe Some(baseTenantInfo)
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

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.map(_.getEditable) shouldBe Some(baseTenantInfo)
          successVal.newInfo.map(_.getEditable) shouldBe Some(updatedInfo)
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

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.map(_.getEditable) shouldBe Some(baseTenantInfo)
          successVal.newInfo.map(_.getEditable) shouldBe Some(
            baseTenantInfo.copy(
              name = Some(newName),
              organizations = Some(newOrgs)
            )
          )
        }
      }

      "executing ActiveTenant command" should {
        "error on incomplete info" in {
          val (tenantId, p, probe) = createTestVariables()
          establishTenant(tenantId, p, probe, None)

          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
            ),
            probe.ref
          )

          val response: StatusReply[TenantResponse] = probe.receiveMessage()

          assert(response.isError)

          val responseError: Throwable = response.getError
          responseError.getMessage shouldEqual "No associated name"

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(EditableTenantInfo(name = Some(baseTenantInfo.getName)))
            ),
            probe.ref
          )
          assert(probe.receiveMessage().isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser"))
            ),
            probe.ref
          )

          val response2: StatusReply[TenantResponse] = probe.receiveMessage()

          assert(response2.isError)

          val responseError2: Throwable = response2.getError
          responseError2.getMessage shouldEqual "No associated primary contact"

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(EditableTenantInfo(primaryContact = Some(baseContact), address = Some(baseAddress)))
            ),
            probe.ref
          )
          assert(probe.receiveMessage().isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser"))
            ),
            probe.ref
          )

          val response3: StatusReply[TenantResponse] = probe.receiveMessage()

          assert(response3.isError)

          val responseError3: Throwable = response3.getError
          responseError3.getMessage shouldEqual "No associated organizations"
        }

        "error and return the proper response when Activating an already active tenant" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("activatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("activatingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              onBehalfOf = Some(MemberId("unauthorizedUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantSuspendedValue

          successVal.tenantId shouldEqual Some(TenantId(tenantId))
          successVal.suspensionReason shouldEqual "reason1"

          val tenantSuspendedMeta = successVal.getMetaInfo

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("suspendingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            GetOrganizations(
              tenantId = Some(TenantId(tenantId))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          successVal.getOrganizations.value shouldEqual
            Seq(
              OrganizationId("org1"),
              OrganizationId("org2")
            )
        }
      }

      "executing TerminateTenant command" should {
        "error for an unauthorized terminating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            TerminateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("terminatingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            TerminateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("terminatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal =
            response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantTerminatedValue
          successVal.tenantId.map(_.id) shouldBe Some(tenantId)

          val metaInfo = successVal.getMetaInfo

          metaInfo.createdBy.map(_.id) shouldBe Some("establishingUser")
          metaInfo.lastUpdatedBy.map(_.id) shouldBe Some("terminatingUser")
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("unauthorizedUser")),
              Some(EditableTenantInfo()),
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendResponse = probe.receiveMessage()
          assert(suspendResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(EditableTenantInfo()),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.map(_.getInfo) shouldEqual Some(baseTenantInfo.toInfo)
          successVal.newInfo.map(_.getInfo) shouldEqual Some(baseTenantInfo.toInfo)
        }

        "succeed for an edit of all fields and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              onBehalfOf = Some(MemberId("suspendingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.map(_.getInfo) shouldEqual Some(baseTenantInfo.toInfo)
          successVal.newInfo.map(_.getInfo) shouldEqual Some(updatedInfo.toInfo)
        }

        "succeed for a partial edit and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              onBehalfOf = Some(MemberId("suspendingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              Some(TenantId(tenantId)),
              Some(MemberId("someUser")),
              Some(updatedInfo),
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue

          successVal.oldInfo.map(_.getInfo) shouldEqual Some(baseTenantInfo.toInfo)
          successVal.newInfo.map(_.getInfo) shouldEqual Some(
            baseTenantInfo
              .copy(
                name = Some(newName),
                organizations = Some(newOrgs)
              )
              .toInfo
          )
        }
      }

      "executing ActiveTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          // Change state to active
          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("activatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantActivatedValue

          successVal.tenantId shouldEqual Some(TenantId(tenantId))

          val tenantSuspendedMeta = successVal.getMetaInfo

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("activatingUser"))
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              onBehalfOf = Some(MemberId("updatingUser1"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantSuspendedValue

          successVal.tenantId shouldEqual Some(TenantId(tenantId))
          successVal.suspensionReason shouldEqual "reason1"

          val tenantSuspendedMeta = successVal.getMetaInfo

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser1"))
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("suspendingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            GetOrganizations(
              tenantId = Some(TenantId(tenantId))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue
          successVal.getOrganizations.value shouldEqual
            Seq(
              OrganizationId("org1"),
              OrganizationId("org2")
            )
        }
      }

      "executing TerminateTenant command" should {
        "succeed for the golden path" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            TerminateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("terminatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal =
            response.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantTerminatedValue
          successVal.tenantId.map(_.id) shouldBe Some(tenantId)

          val metaInfo = successVal.getMetaInfo

          metaInfo.createdBy.map(_.id) shouldBe Some("establishingUser")
          metaInfo.lastUpdatedBy.map(_.id) shouldBe Some("terminatingUser")
        }
      }
    }

    "in the Terminated state" when {
      "executing EditInfo command" should {
        "error as an action not permitted" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            EditInfo(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("editingUser")),
              infoToUpdate = Some(
                EditableTenantInfo(
                  baseTenantInfo.name,
                  Some(baseContact),
                  Some(baseAddress),
                  baseTenantInfo.organizations
                )
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

          p ! Tenant.TenantRequestEnvelope(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("activatingUser"))
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

          p ! Tenant.TenantRequestEnvelope(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason",
              onBehalfOf = Some(MemberId("suspendingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          response.getError.getMessage shouldEqual "Command not allowed in Terminated state"
        }
      }

      "executing GetOrganizations command" should {
        "error that command is not allowed" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            GetOrganizations(
              tenantId = Some(TenantId(tenantId))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)
          assert(response.getError.getMessage eq "Command not allowed in Terminated state")
        }
      }

      "executing TerminateTenant command" should {
        "error as an action not permitted" in {
          val (tenantId, p, probe) = createTestVariables()

          val terminateResponse = terminateTenant(tenantId, p, probe)
          assert(terminateResponse.isSuccess)

          p ! Tenant.TenantRequestEnvelope(
            TerminateTenant(
              tenantId = Some(TenantId(tenantId)),
              onBehalfOf = Some(MemberId("terminatingUser"))
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
