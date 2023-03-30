package com.improving.app.tenant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, MemberId, OrganizationId, PostalCodeMessageImpl, TenantId}
import com.improving.app.tenant.domain.Tenant.TenantCommand
import com.improving.app.tenant.domain._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import akka.persistence.typed.PersistenceId
import com.improving.app.tenant.TestData._

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
            UpdateTenantName(Some(TenantId(tenantId)), "name", Some(MemberId("user"))),
            UpdateAddress(Some(TenantId(tenantId)), Some(baseAddress), Some(MemberId("user"))),
            UpdatePrimaryContact(Some(TenantId(tenantId)), Some(baseContact), Some(MemberId("user"))),
            AddOrganizations(Some(TenantId(tenantId)), Seq(), Some(MemberId("user"))),
            RemoveOrganizations(Some(TenantId(tenantId)), Seq(), Some(MemberId("user"))),
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
      "executing UpdateTenantName command" should {
        "error for an unauthorized updating user" ignore {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed for the golden path and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val tenantInfo = new TenantInfo(
            name = "newName"
          )

          val establishTenantResponse = establishTenant(tenantId, p, probe, tenantInfo)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantNameUpdatedValue.isDefined)

          val tenantNameUpdated = successVal.asMessage.sealedValue.tenantNameUpdatedValue.get

          tenantNameUpdated.tenantId shouldEqual Some(TenantId(tenantId))
          tenantNameUpdated.oldName shouldEqual "newName"
          tenantNameUpdated.newName shouldEqual "name1"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val updateTenantNameResponse1 = probe.receiveMessage()
          assert(updateTenantNameResponse1.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "Tenant name is already in use"
        }
      }

      "executing UpdatePrimaryContact command" should {
        "error for an unauthorized updating user" ignore {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact),
              updatingUser = Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for incomplete updating primary contact info" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          // Missing email in the command
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(emailAddress = None)),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Primary contact info is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val tenantInfo = TenantInfo(
            name = "Name",
            primaryContact = Some(baseContact)
          )

          val establishTenantResponse = establishTenant(tenantId, p, probe, tenantInfo)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(
                Contact(
                  firstName = "firstName1",
                  lastName = "lastName1",
                  emailAddress = Some("test1@test.com"),
                  phone = Some("111-111-1112"),
                  userName = "contactUsername1"
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          //make the assertions for this test
          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.primaryContactUpdatedValue.isDefined)

          val primaryContactUpdated = successVal.asMessage.sealedValue.primaryContactUpdatedValue.get

          primaryContactUpdated.tenantId shouldEqual Some(TenantId(tenantId))
          primaryContactUpdated.oldContact shouldEqual Some(baseContact)
          primaryContactUpdated.newContact shouldEqual Some(
            Contact(
              firstName = "firstName1",
              lastName = "lastName1",
              emailAddress = Some("test1@test.com"),
              phone = Some("111-111-1112"),
              userName = "contactUsername1"
            )
          )

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress),
              updatingUser = Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for an incomplete address" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          // City is not present
          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(city = "")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val tenantInfo = TenantInfo(
            name = "name",
            address = Some(Address(
              line1 = "line1",
              line2 = "line2",
              city = "city",
              stateProvince = "stateProvince",
              country = "country",
              postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
            ))
          )
          val establishTenantResponse = establishTenant(tenantId, p, probe, tenantInfo)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(
                Address(
                  line1 = "line3",
                  line2 = "line4",
                  city = "city1",
                  stateProvince = "stateProvince1",
                  country = "country1",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.addressUpdatedValue.isDefined)

          val addressUpdated = successVal.asMessage.sealedValue.addressUpdatedValue.get
          addressUpdated.tenantId shouldEqual Some(TenantId(tenantId))
          addressUpdated.oldAddress shouldEqual Some(baseAddress)
          addressUpdated.newAddress shouldEqual Some(
            Address(
              line1 = "line3",
              line2 = "line4",
              city = "city1",
              stateProvince = "stateProvince1",
              country = "country1",
              postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
            )
          )

          assert(addressUpdated.metaInfo.isDefined)

          val addressUpdatedMeta = addressUpdated.metaInfo.get

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing AddOrganizations command" should {
        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq.empty,
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "No organizations to add"
        }

        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("unauthorizedUser"))
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

          val tenantInfo = TenantInfo(
            name = "name",
            orgs = Seq(OrganizationId("org1"))
          )

          val establishTenantResponse = establishTenant(tenantId, p, probe, tenantInfo)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org2")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.organizationsAddedValue.isDefined)

          val organizationAdded = successVal.asMessage.sealedValue.organizationsAddedValue.get
          organizationAdded.tenantId shouldEqual Some(TenantId(tenantId))
          organizationAdded.newOrgsList shouldEqual Seq(OrganizationId("org1"), OrganizationId("org2"))

          assert(organizationAdded.metaInfo.isDefined)

          val organizationAddedMeta = organizationAdded.metaInfo.get

          organizationAddedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          organizationAddedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val tenantInfo = TenantInfo(
            name = "name",
            orgs = Seq(OrganizationId("org1"))
          )

          val establishTenantResponse = establishTenant(tenantId, p, probe, tenantInfo)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Organization ids already present for the tenant is not allowed"
        }
      }

      "executing RemoveOrganizations command" should {
        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq.empty,
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "No organizations to remove"
        }

        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("unauthorizedUser"))
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

          val tenantInfo = TenantInfo(
            name = "name",
            orgs = Seq(OrganizationId("org1"))
          )

          val establishTenantResponse = establishTenant(tenantId, p, probe, tenantInfo)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.organizationsRemovedValue.isDefined)

          val organizationRemoved = successVal.asMessage.sealedValue.organizationsRemovedValue.get
          organizationRemoved.tenantId shouldEqual Some(TenantId(tenantId))
          organizationRemoved.newOrgsList shouldEqual Seq.empty

          assert(organizationRemoved.metaInfo.isDefined)

          val organizationRemovedMeta = organizationRemoved.metaInfo.get

          organizationRemovedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          organizationRemovedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids not already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishTenantResponse = establishTenant(tenantId, p, probe)
          assert(establishTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org2")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Organization ids not already present for the tenant is not allowed"
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
      "executing UpdateTenantName command" should {
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

          // Test command in question
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed for the golden path and return the proper response" in {
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
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantNameUpdatedValue.isDefined)

          val tenantNameUpdated = successVal.asMessage.sealedValue.tenantNameUpdatedValue.get

          tenantNameUpdated.tenantId shouldEqual Some(TenantId(tenantId))
          tenantNameUpdated.oldName shouldEqual "Tenant Name"
          tenantNameUpdated.newName shouldEqual "newName"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
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
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val updateTenantNameResponse1 = probe.receiveMessage()
          assert(updateTenantNameResponse1.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "Tenant name is already in use"
        }
      }

      "executing UpdatePrimaryContact command" should {
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
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for incomplete updating primary contact info" in {
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

          // Missing email in the command
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(emailAddress = None)),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Primary contact info is not complete"
        }

        "succeed for the golden path and return the proper response" in {
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

          val newContact = baseContact.copy(firstName = "bob")

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(newContact),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          //make the assertions for this test
          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.primaryContactUpdatedValue.isDefined)

          val primaryContactUpdated = successVal.asMessage.sealedValue.primaryContactUpdatedValue.get

          primaryContactUpdated.tenantId shouldEqual Some(TenantId(tenantId))
          primaryContactUpdated.oldContact shouldEqual Some(baseContact)
          primaryContactUpdated.newContact shouldEqual Some(newContact)

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
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
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress),
              Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for an incomplete address" in {
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

          // City is not present
          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(city = "")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"
        }

        "succeed for the golden path and return the proper response" in {
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

          val newAddress = baseAddress.copy(line1 = "new line")

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(newAddress),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.addressUpdatedValue.isDefined)

          val addressUpdated = successVal.asMessage.sealedValue.addressUpdatedValue.get
          addressUpdated.tenantId shouldEqual Some(TenantId(tenantId))
          addressUpdated.oldAddress shouldEqual Some(baseAddress)
          addressUpdated.newAddress shouldEqual Some(newAddress)

          assert(addressUpdated.metaInfo.isDefined)

          val addressUpdatedMeta = addressUpdated.metaInfo.get

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing AddOrganizations command" should {
        "error for bad message input" in {
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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq.empty,
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "No organizations to add"
        }

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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("unauthorizedUser"))
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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.organizationsAddedValue.isDefined)

          val organizationAdded = successVal.asMessage.sealedValue.organizationsAddedValue.get
          organizationAdded.tenantId shouldEqual Some(TenantId(tenantId))
          organizationAdded.newOrgsList shouldEqual Seq(OrganizationId("org1"))

          assert(organizationAdded.metaInfo.isDefined)

          val organizationAddedMeta = organizationAdded.metaInfo.get

          organizationAddedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          organizationAddedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids already in the list" in {
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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val firstOrganizationResponse = probe.receiveMessage()
          assert(firstOrganizationResponse.isSuccess)

          p ! Tenant.TenantCommand(
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Organization ids already present for the tenant is not allowed"
        }
      }

      "executing RemoveOrganizations command" should {
        "error for bad message input" in {
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
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq.empty,
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "No organizations to remove"
        }

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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val addOrganizationsResponse = probe.receiveMessage()
          assert(addOrganizationsResponse.isSuccess)

          p ! Tenant.TenantCommand(
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("unauthorizedUser"))
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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val addOrganizationsResponse = probe.receiveMessage()
          assert(addOrganizationsResponse.isSuccess)

          p ! Tenant.TenantCommand(
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.organizationsRemovedValue.isDefined)

          val organizationRemoved = successVal.asMessage.sealedValue.organizationsRemovedValue.get
          organizationRemoved.tenantId shouldEqual Some(TenantId(tenantId))
          organizationRemoved.newOrgsList shouldEqual Seq.empty

          assert(organizationRemoved.metaInfo.isDefined)

          val organizationRemovedMeta = organizationRemoved.metaInfo.get

          organizationRemovedMeta.createdBy shouldEqual Some(MemberId("establishingUser"))
          organizationRemovedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids not already in the list" in {
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
            RemoveOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Organization ids not already present for the tenant is not allowed"
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

          val response = {
            // Populate tenant name
            p ! Tenant.TenantCommand(
              UpdateTenantName(
                tenantId = Some(TenantId(tenantId)),
                newName = "newName",
                updatingUser = Some(MemberId("updatingUser"))
              ),
              probe.ref
            )

            val updateTenantNameResponse = probe.receiveMessage()
            assert(updateTenantNameResponse.isSuccess)

            // Populate primary contact
            p ! Tenant.TenantCommand(
              UpdatePrimaryContact(
                tenantId = Some(TenantId(tenantId)),
                newContact = Some(baseContact),
                updatingUser = Some(MemberId("updatingUser"))
              ),
              probe.ref
            )

            val updateAddressResponse = probe.receiveMessage()
            assert(updateAddressResponse.isSuccess)

            // Populate address
            p ! Tenant.TenantCommand(
              UpdateAddress(
                tenantId = Some(TenantId(tenantId)),
                newAddress = Some(baseAddress),
                updatingUser = Some(MemberId("updatingUser"))
              ),
              probe.ref
            )

            val updatePrimaryContactResponse = probe.receiveMessage()
            assert(updatePrimaryContactResponse.isSuccess)

            // Populate organizations
            p ! Tenant.TenantCommand(
              AddOrganizations(
                tenantId = Some(TenantId(tenantId)),
                orgId = Seq(OrganizationId("org1")),
                updatingUser = Some(MemberId("updatingUser"))
              ),
              probe.ref
            )

            val addOrganizationsResponse = probe.receiveMessage()
            assert(addOrganizationsResponse.isSuccess)

            // Change state to active
            p ! Tenant.TenantCommand(
              ActivateTenant(
                tenantId = Some(TenantId(tenantId)),
                activatingUser = Some(MemberId("activatingUser"))
              ),
              probe.ref
            )

            probe.receiveMessage()
          }
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
//2551