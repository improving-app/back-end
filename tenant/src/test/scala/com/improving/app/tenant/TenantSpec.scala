package com.improving.app.tenant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.improving.app.common.domain._
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
    probe: TestProbe[StatusReply[TenantEvent]]
  ): StatusReply[TenantEvent] = {
    p ! Tenant.TenantCommand(
      EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("creatingUser"))
      ),
      probe.ref
    )

    probe.receiveMessage()
  }

  def transitionToActive(
    tenantId: String,
    p: ActorRef[TenantCommand],
    probe: TestProbe[StatusReply[TenantEvent]],
    isTenantAlreadyEstablished: Boolean = false
  ): StatusReply[TenantEvent] = {

    if (!isTenantAlreadyEstablished) {
      val establishedResponse = establishTenant(tenantId, p, probe)
      assert(establishedResponse.isSuccess)
    }

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
        activatingUser = Some(MemberId("updatingUser"))
      ),
      probe.ref
    )

    probe.receiveMessage()
  }

  "A Tenant Actor" when {
    // Should this also error out when other tenants have the same name?
    "in the Draft State" when {
      "executing EstablishTenant command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("unauthorizedUser"))
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
              establishingUser = Some(MemberId("creatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          successVal.asMessage.sealedValue.tenantEstablishedValue.get.tenantId shouldBe Some(TenantId(tenantId))
          successVal.asMessage.sealedValue.tenantEstablishedValue.get.metaInfo.get.createdBy shouldBe Some(MemberId("creatingUser"))
        }

        "error for a tenant that is already established" in {
          val (tenantId, p, probe) = createTestVariables()

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("creatingUser"))
            ),
            probe.ref
          )

          val establishResponse = probe.receiveMessage()
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            EstablishTenant(
              tenantId = Some(TenantId(tenantId)),
              establishingUser = Some(MemberId("creatingUser"))
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Tenant Id is being used for another tenant"
        }
      }

      "executing UpdateTenantName command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "newName",
              updatingUser = Some(MemberId("unauthorizedUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)


          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = None,
              newName = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isError)
          val responseError1 = response1.getError
          responseError1.getMessage shouldEqual "Tenant Id is not set"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("")),
              newName = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)
          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Tenant Id is empty"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "",
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response3 = probe.receiveMessage()
          assert(response3.isError)
          val responseError3 = response3.getError
          responseError3.getMessage shouldEqual "Updating tenant name is empty"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "newName",
              updatingUser = None
            ),
            probe.ref
          )

          val response4 = probe.receiveMessage()
          assert(response4.isError)
          val responseError4 = response4.getError
          responseError4.getMessage shouldEqual "Updating user Id is not set"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId(tenantId)),
              newName = "newName",
              updatingUser = Some(MemberId(""))
            ),
            probe.ref
          )

          val response5 = probe.receiveMessage()
          assert(response5.isError)
          val responseError5 = response5.getError
          responseError5.getMessage shouldEqual "Updating user Id is empty"
        }

        "succeed for the golden path and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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
          tenantNameUpdated.oldName shouldEqual ""
          tenantNameUpdated.newName shouldEqual "newName"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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

        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = None,
              Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isError)
          val responseError1 = response1.getError
          responseError1.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(firstName = "")),
              Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)
          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(lastName = "")),
              Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response3 = probe.receiveMessage()
          assert(response3.isError)
          val responseError3 = response3.getError
          responseError3.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(emailAddress = Some(""))),
              Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response4 = probe.receiveMessage()
          assert(response4.isError)
          val responseError4 = response4.getError
          responseError4.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(phone = Some(""))),
              Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response5 = probe.receiveMessage()
          assert(response5.isError)
          val responseError5 = response5.getError
          responseError5.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact.copy(userName = "")),
              Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response6 = probe.receiveMessage()
          assert(response6.isError)
          val responseError6 = response6.getError
          responseError6.getMessage shouldEqual "Primary contact info is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId(tenantId)),
              newContact = Some(baseContact),
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
          primaryContactUpdated.oldContact shouldEqual None
          primaryContactUpdated.newContact shouldEqual Some(baseContact)

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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

        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = None,
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)
          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(line1 = "")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isError)
          val responseError1 = response1.getError
          responseError1.getMessage shouldEqual "Address information is not complete"

          // Notice line2 is not validated as it is assumed that line2 is optional

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(city = "")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)
          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(stateProvince = "")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response3 = probe.receiveMessage()
          assert(response3.isError)
          val responseError3 = response3.getError
          responseError3.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(country = "")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response4 = probe.receiveMessage()
          assert(response4.isError)
          val responseError4 = response4.getError
          responseError4.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(postalCode = None)),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response5 = probe.receiveMessage()
          assert(response5.isError)
          val responseError5 = response5.getError
          responseError5.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress.copy(postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl(""))))),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response6 = probe.receiveMessage()
          assert(response6.isError)
          val responseError6 = response6.getError
          responseError6.getMessage shouldEqual "Address information is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId(tenantId)),
              newAddress = Some(baseAddress),
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
          addressUpdated.oldAddress shouldEqual None
          addressUpdated.newAddress shouldEqual Some(baseAddress)

          assert(addressUpdated.metaInfo.isDefined)

          val addressUpdatedMeta = addressUpdated.metaInfo.get

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing AddOrganizations command" should {
        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1"), OrganizationId("org1")),
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

          organizationAddedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          organizationAddedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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

          p ! Tenant.TenantCommand(
            AddOrganizations(
              tenantId = Some(TenantId(tenantId)),
              orgId = Seq(OrganizationId("org1")),
              updatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isError)

          val responseError = response1.getError
          responseError.getMessage shouldEqual "Organization ids already present for the tenant is not allowed"
        }
      }

      "executing RemoveOrganizations command" should {
        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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

          organizationRemovedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          organizationRemovedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids not already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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

      "executing ActivateTenant command" should {
        "error for incomplete info to transition the tenant" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              activatingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Draft tenants may not transition to the Active state with incomplete required fields"
        }

        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

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

          val response = transitionToActive(tenantId, p, probe)
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantActivatedValue.isDefined)

          val tenantActivated = successVal.asMessage.sealedValue.tenantActivatedValue.get

          tenantActivated.tenantId shouldEqual Some(TenantId(tenantId))

          assert(tenantActivated.metaInfo.isDefined)

          val tenantActivatedMeta = tenantActivated.metaInfo.get

          tenantActivatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantActivatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
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
              suspendingUser = Some(MemberId("updatingUser"))
            ),
            probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantSuspendedValue.isDefined)

          val tenantSuspended = successVal.asMessage.sealedValue.tenantSuspendedValue.get

          tenantSuspended.tenantId shouldEqual Some(TenantId(tenantId))
          tenantSuspended.suspensionReason shouldEqual "reason"

          assert(tenantSuspended.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantSuspended.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }
    }

    "in the Active state" when {
      "executing UpdateTenantName command" should {
        "error for an unauthorized updating user" ignore {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          // Transition to Active state
          val (tenantId, p, probe) = createTestVariables()

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
        "error for an unauthorized updating user" ignore {
          val (tenantId, p, probe) = createTestVariables()

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing AddOrganizations command" should {
        "error for bad message input" in {
          val (tenantId, p, probe) = createTestVariables()

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          organizationAddedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          organizationAddedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          organizationRemovedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          organizationRemovedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids not already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId(tenantId)),
              activatingUser = Some(MemberId("updatingUser"))
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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

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

          val tenantActivatedResponse = transitionToActive(tenantId, p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspensionReason = "reason1",
              suspendingUser = Some(MemberId("updatingUser"))
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

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
          tenantNameUpdated.oldName shouldEqual ""
          tenantNameUpdated.newName shouldEqual "newName"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
          primaryContactUpdated.oldContact shouldEqual None
          primaryContactUpdated.newContact shouldEqual Some(baseContact)

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
          addressUpdated.oldAddress shouldEqual None
          addressUpdated.newAddress shouldEqual Some(baseAddress)

          assert(addressUpdated.metaInfo.isDefined)

          val addressUpdatedMeta = addressUpdated.metaInfo.get

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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

          organizationAddedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          organizationAddedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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

          organizationRemovedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          organizationRemovedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for organization ids not already in the list" in {
          val (tenantId, p, probe) = createTestVariables()

          val establishResponse = establishTenant(tenantId, p, probe)
          assert(establishResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId(tenantId)),
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ),
            probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          val response = transitionToActive(tenantId, p, probe, isTenantAlreadyEstablished = true)
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantActivatedValue.isDefined)

          val tenantActivated = successVal.asMessage.sealedValue.tenantActivatedValue.get

          tenantActivated.tenantId shouldEqual Some(TenantId(tenantId))

          assert(tenantActivated.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantActivated.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
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
              suspendingUser = Some(MemberId("updatingUser")),
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
              suspendingUser = Some(MemberId("updatingUser"))
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

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId("creatingUser"))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser1"))
        }
      }
    }
  }
}
//2551