package com.improving.app.tenant

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, MemberId, PostalCodeMessageImpl, TenantId}
import com.improving.app.tenant.domain.Tenant.TenantCommand
import com.improving.app.tenant.domain.{ActivateTenant, SuspendTenant, UpdateAddress, UpdatePrimaryContact}

import scala.util.Random
import akka.pattern.StatusReply
import com.improving.app.tenant.domain.{Tenant, TenantEvent, UpdateTenantName}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

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

  def transitionToActive(p: ActorRef[TenantCommand], probe: TestProbe[StatusReply[TenantEvent]]): StatusReply[TenantEvent] = {

    // Populate tenant name
    p ! Tenant.TenantCommand(
      UpdateTenantName(
        tenantId = Some(TenantId("testTenantId")),
        newName = "newName",
        updatingUser = Some(MemberId("updatingUser"))
      ), probe.ref
    )

    val updateTenantNameResponse = probe.receiveMessage()
    assert(updateTenantNameResponse.isSuccess)

    // Populate primary contact
    p ! Tenant.TenantCommand(
      UpdatePrimaryContact(
        tenantId = Some(TenantId("testTenantId")),
        newContact = Some(
          Contact(
            firstName = "firstName",
            lastName = "lastName",
            emailAddress = "test@test.com",
            phone = "111-111-1111",
            userName = "contactUsername"
          )
        ),
        updatingUser = Some(MemberId("updatingUser"))
      ), probe.ref
    )

    val updateAddressResponse = probe.receiveMessage()
    assert(updateAddressResponse.isSuccess)

    // Populate address
    p ! Tenant.TenantCommand(
      UpdateAddress(
        tenantId = Some(TenantId("testTenantId")),
        newAddress = Some(
          Address(
            line1 = "line1",
            line2 = "line2",
            city = "city",
            stateProvince = "stateProvince",
            country = "country",
            postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
          )
        ), updatingUser = Some(MemberId("updatingUser"))
      ), probe.ref
    )

    val updatePrimaryContactResponse = probe.receiveMessage()
    assert(updatePrimaryContactResponse.isSuccess)

    // Change state to active
    p ! Tenant.TenantCommand(
      ActivateTenant(
        tenantId = Some(TenantId("testTenantId")),
        updatingUser = Some(MemberId("updatingUser"))
      ), probe.ref
    )

    probe.receiveMessage()
  }

  "A Tenant Actor" when {
    // Should this also error out when other tenants have the same name?
    "in the Draft State" when {
      "executing UpdateTenantName command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "newName",
              updatingUser = Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for bad message input" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = None,
              newName  = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
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
            ), probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)
          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Tenant Id is empty"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response3 = probe.receiveMessage()
          assert(response3.isError)
          val responseError3 = response3.getError
          responseError3.getMessage shouldEqual "Updating tenant name is empty"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "newName",
              updatingUser = None
            ), probe.ref
          )

          val response4 = probe.receiveMessage()
          assert(response4.isError)
          val responseError4 = response4.getError
          responseError4.getMessage shouldEqual "Updating user Id is not set"

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "newName",
              updatingUser = Some(MemberId(""))
            ), probe.ref
          )

          val response5 = probe.receiveMessage()
          assert(response5.isError)
          val responseError5 = response5.getError
          responseError5.getMessage shouldEqual "Updating user Id is empty"
        }

        "succeed for the golden path and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantNameUpdatedValue.isDefined)

          val tenantNameUpdated = successVal.asMessage.sealedValue.tenantNameUpdatedValue.get

          tenantNameUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          tenantNameUpdated.oldName shouldEqual ""
          tenantNameUpdated.newName shouldEqual "newName"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "Tenant name is already in use"
        }
      }

      "executing UpdatePrimaryContact command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              updatingUser = Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for bad message input" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = None,
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isError)
          val responseError1 = response1.getError
          responseError1.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)
          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response3 = probe.receiveMessage()
          assert(response3.isError)
          val responseError3 = response3.getError
          responseError3.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response4 = probe.receiveMessage()
          assert(response4.isError)
          val responseError4 = response4.getError
          responseError4.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response5 = probe.receiveMessage()
          assert(response5.isError)
          val responseError5 = response5.getError
          responseError5.getMessage shouldEqual "Primary contact info is not complete"

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = ""
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response6 = probe.receiveMessage()
          assert(response6.isError)
          val responseError6 = response6.getError
          responseError6.getMessage shouldEqual "Primary contact info is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          //make the assertions for this test
          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.primaryContactUpdatedValue.isDefined)

          val primaryContactUpdated = successVal.asMessage.sealedValue.primaryContactUpdatedValue.get

          primaryContactUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          primaryContactUpdated.oldContact shouldEqual None
          primaryContactUpdated.newContact shouldEqual Some(
            Contact(
              firstName = "firstName",
              lastName = "lastName",
              emailAddress = "test@test.com",
              phone = "111-111-1111",
              userName = "contactUsername"
            )
          )

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for bad message input" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = None, updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)
          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response1 = probe.receiveMessage()
          assert(response1.isError)
          val responseError1 = response1.getError
          responseError1.getMessage shouldEqual "Address information is not complete"

          // Notice line2 is not validated as it is assumed that line2 is optional

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)
          val responseError2 = response2.getError
          responseError2.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response3 = probe.receiveMessage()
          assert(response3.isError)
          val responseError3 = response3.getError
          responseError3.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response4 = probe.receiveMessage()
          assert(response4.isError)
          val responseError4 = response4.getError
          responseError4.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = None
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response5 = probe.receiveMessage()
          assert(response5.isError)
          val responseError5 = response5.getError
          responseError5.getMessage shouldEqual "Address information is not complete"

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response6 = probe.receiveMessage()
          assert(response6.isError)
          val responseError6 = response6.getError
          responseError6.getMessage shouldEqual "Address information is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.addressUpdatedValue.isDefined)

          val addressUpdated = successVal.asMessage.sealedValue.addressUpdatedValue.get
          addressUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          addressUpdated.oldAddress shouldEqual None
          addressUpdated.newAddress shouldEqual Some(
            Address(
              line1 = "line1",
              line2 = "line2",
              city = "city",
              stateProvince = "stateProvince",
              country = "country",
              postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
            )
          )

          assert(addressUpdated.metaInfo.isDefined)

          val addressUpdatedMeta = addressUpdated.metaInfo.get

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing ActivateTenant command" should {
        "error for incomplete info to transition the tenant" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Draft tenants may not transition to the Active state with incomplete required fields"
        }

        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          // Populate tenant name
          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val updateTenantNameResponse = probe.receiveMessage()
          assert(updateTenantNameResponse.isSuccess)

          // Populate primary contact
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val updateAddressResponse = probe.receiveMessage()
          assert(updateAddressResponse.isSuccess)

          // Populate address
          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val updatePrimaryContactResponse = probe.receiveMessage()
          assert(updatePrimaryContactResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId("testTenantId")),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val response = transitionToActive(p, probe)
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantActivatedValue.isDefined)

          val tenantActivated = successVal.asMessage.sealedValue.tenantActivatedValue.get

          tenantActivated.tenantId shouldEqual Some(TenantId("testTenantId"))

          assert(tenantActivated.metaInfo.isDefined)

          val tenantActivatedMeta = tenantActivated.metaInfo.get

          tenantActivatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantActivatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason",
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantSuspendedValue.isDefined)

          val tenantSuspended = successVal.asMessage.sealedValue.tenantSuspendedValue.get

          tenantSuspended.tenantId shouldEqual Some(TenantId("testTenantId"))
          tenantSuspended.suspensionReason shouldEqual "reason"

          assert(tenantSuspended.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantSuspended.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }
    }

    "in the Active state" when {
      "executing UpdateTenantName command" should {
        "error for an unauthorized updating user" ignore {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed for the golden path and return the proper response" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantNameUpdatedValue.isDefined)

          val tenantNameUpdated = successVal.asMessage.sealedValue.tenantNameUpdatedValue.get

          tenantNameUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          tenantNameUpdated.oldName shouldEqual "newName"
          tenantNameUpdated.newName shouldEqual "name1"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val updateTenantNameResponse1 = probe.receiveMessage()
          assert(updateTenantNameResponse1.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
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
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              updatingUser = Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for incomplete updating primary contact info" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          // Missing email in the command
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Primary contact info is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName1",
                  lastName = "lastName1",
                  emailAddress = "test1@test.com",
                  phone = "111-111-1112",
                  userName = "contactUsername1"
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          //make the assertions for this test
          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.primaryContactUpdatedValue.isDefined)

          val primaryContactUpdated = successVal.asMessage.sealedValue.primaryContactUpdatedValue.get

          primaryContactUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          primaryContactUpdated.oldContact shouldEqual Some(
            Contact(
              firstName = "firstName",
              lastName = "lastName",
              emailAddress = "test@test.com",
              phone = "111-111-1111",
              userName = "contactUsername"
            )
          )
          primaryContactUpdated.newContact shouldEqual Some(
            Contact(
              firstName = "firstName1",
              lastName = "lastName1",
              emailAddress = "test1@test.com",
              phone = "111-111-1112",
              userName = "contactUsername1"
            )
          )

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for an incomplete address" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          // City is not present
          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line3",
                  line2 = "line4",
                  city = "city1",
                  stateProvince = "stateProvince1",
                  country = "country1",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.addressUpdatedValue.isDefined)

          val addressUpdated = successVal.asMessage.sealedValue.addressUpdatedValue.get
          addressUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          addressUpdated.oldAddress shouldEqual Some(
            Address(
              line1 = "line1",
              line2 = "line2",
              city = "city",
              stateProvince = "stateProvince",
              country = "country",
              postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
            )
          )
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

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing ActiveTenant command" should {
        "error and return the proper response" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Active tenants may not transition to the Active state"
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason",
              updatingUser = Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          // Transition to Active state
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          val tenantActivatedResponse = transitionToActive(p, probe)
          assert(tenantActivatedResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantSuspendedValue.isDefined)

          val tenantSuspended = successVal.asMessage.sealedValue.tenantSuspendedValue.get

          tenantSuspended.tenantId shouldEqual Some(TenantId("testTenantId"))
          tenantSuspended.suspensionReason shouldEqual "reason1"

          assert(tenantSuspended.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantSuspended.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }
    }

    "in the Suspended state" when {
      "executing UpdateTenantName command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          // Test command in question
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )
          val response2 = probe.receiveMessage()

          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed for the golden path and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "newName",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantNameUpdatedValue.isDefined)

          val tenantNameUpdated = successVal.asMessage.sealedValue.tenantNameUpdatedValue.get

          tenantNameUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          tenantNameUpdated.oldName shouldEqual ""
          tenantNameUpdated.newName shouldEqual "newName"

          assert(tenantNameUpdated.metaInfo.isDefined)

          val tenantNameUpdatedMeta = tenantNameUpdated.metaInfo.get
          tenantNameUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantNameUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }

        "error for the same tenant name as the old one" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val updateTenantNameResponse1 = probe.receiveMessage()
          assert(updateTenantNameResponse1.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateTenantName(
              tenantId = Some(TenantId("testTenantId")),
              newName = "name1",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response2 = probe.receiveMessage()
          assert(response2.isError)

          val responseError = response2.getError
          responseError.getMessage shouldEqual "Tenant name is already in use"
        }
      }

      "executing UpdatePrimaryContact command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for incomplete updating primary contact info" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          // Missing email in the command
          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()

          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Primary contact info is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdatePrimaryContact(
              tenantId = Some(TenantId("testTenantId")),
              newContact = Some(
                Contact(
                  firstName = "firstName",
                  lastName = "lastName",
                  emailAddress = "test@test.com",
                  phone = "111-111-1111",
                  userName = "contactUsername"
                )
              ),
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          //make the assertions for this test
          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.primaryContactUpdatedValue.isDefined)

          val primaryContactUpdated = successVal.asMessage.sealedValue.primaryContactUpdatedValue.get

          primaryContactUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          primaryContactUpdated.oldContact shouldEqual None
          primaryContactUpdated.newContact shouldEqual Some(
            Contact(
              firstName = "firstName",
              lastName = "lastName",
              emailAddress = "test@test.com",
              phone = "111-111-1111",
              userName = "contactUsername"
            )
          )

          assert(primaryContactUpdated.metaInfo.isDefined)

          val primaryContactUpdatedMeta = primaryContactUpdated.metaInfo.get

          primaryContactUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          primaryContactUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing UpdateAddress command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "error for an incomplete address" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          // City is not present
          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "Address information is not complete"
        }

        "succeed for the golden path and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            UpdateAddress(
              tenantId = Some(TenantId("testTenantId")),
              newAddress = Some(
                Address(
                  line1 = "line1",
                  line2 = "line2",
                  city = "city",
                  stateProvince = "stateProvince",
                  country = "country",
                  postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
                )
              ), updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.addressUpdatedValue.isDefined)

          val addressUpdated = successVal.asMessage.sealedValue.addressUpdatedValue.get
          addressUpdated.tenantId shouldEqual Some(TenantId("testTenantId"))
          addressUpdated.oldAddress shouldEqual None
          addressUpdated.newAddress shouldEqual Some(
            Address(
              line1 = "line1",
              line2 = "line2",
              city = "city",
              stateProvince = "stateProvince",
              country = "country",
              postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
            )
          )

          assert(addressUpdated.metaInfo.isDefined)

          val addressUpdatedMeta = addressUpdated.metaInfo.get

          addressUpdatedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          addressUpdatedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing ActiveTenant command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            ActivateTenant(
              tenantId = Some(TenantId("testTenantId")),
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          val response = transitionToActive(p, probe)
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantActivatedValue.isDefined)


          val tenantActivated = successVal.asMessage.sealedValue.tenantActivatedValue.get

          tenantActivated.tenantId shouldEqual Some(TenantId("testTenantId"))

          assert(tenantActivated.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantActivated.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser"))
        }
      }

      "executing SuspendTenant command" should {
        "error for an unauthorized updating user" ignore {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              updatingUser = Some(MemberId("updatingUser")),
              suspensionReason = "reason"
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason",
              Some(MemberId("unauthorizedUser"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isError)

          val responseError = response.getError
          responseError.getMessage shouldEqual "User is not authorized to modify Tenant"
        }

        "succeed and return the proper response" in {
          val creatingUser = Random.nextString(31)
          val p = this.testKit.spawn(Tenant(MemberId(creatingUser)))
          val probe = this.testKit.createTestProbe[StatusReply[TenantEvent]]()

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason",
              updatingUser = Some(MemberId("updatingUser"))
            ), probe.ref
          )

          val suspendTenantResponse = probe.receiveMessage()
          assert(suspendTenantResponse.isSuccess)

          p ! Tenant.TenantCommand(
            SuspendTenant(
              tenantId = Some(TenantId("testTenantId")),
              suspensionReason = "reason1",
              updatingUser = Some(MemberId("updatingUser1"))
            ), probe.ref
          )

          val response = probe.receiveMessage()
          assert(response.isSuccess)

          val successVal = response.getValue
          assert(successVal.asMessage.sealedValue.tenantSuspendedValue.isDefined)

          val tenantSuspended = successVal.asMessage.sealedValue.tenantSuspendedValue.get

          tenantSuspended.tenantId shouldEqual Some(TenantId("testTenantId"))
          tenantSuspended.suspensionReason shouldEqual "reason1"

          assert(tenantSuspended.metaInfo.isDefined)

          val tenantSuspendedMeta = tenantSuspended.metaInfo.get

          tenantSuspendedMeta.createdBy shouldEqual Some(MemberId(creatingUser))
          tenantSuspendedMeta.lastUpdatedBy shouldEqual Some(MemberId("updatingUser1"))
        }
      }
    }
  }
}

