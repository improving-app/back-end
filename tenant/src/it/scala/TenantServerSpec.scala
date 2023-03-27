import akka.grpc.GrpcClientSettings
import com.improving.app.common.domain.{CaPostalCodeImpl, Contact, OrganizationId, PostalCodeMessageImpl}
import com.improving.app.tenant.domain.{ActivateTenant, AddOrganizations, EstablishTenant, RemoveOrganizations, SuspendTenant, UpdateAddress, UpdatePrimaryContact, UpdateTenantName}
import com.improving.app.common.domain.{Address, MemberId, TenantId}
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.tenant.api.{TenantService, TenantServiceClient}
import org.scalatest.tagobjects.Retryable

import scala.util.Random

class TenantServerSpec extends ServiceTestContainerSpec(8080, "tenant-service") {
  private def getClient(containers: Containers): TenantService = {
    val (host, port) = getContainerHostPort(containers)
    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    TenantServiceClient(clientSettings)
  }

  behavior of "TestServer in a test container"

  it should "expose a port for tenant-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "properly process UpdateTenantName" taggedAs(Retryable) in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.updateTenantName(UpdateTenantName(
        tenantId = Some(TenantId(tenantId)),
        newName = "newName",
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldName shouldBe ""
      response.newName shouldBe "newName"
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process UpdatePrimaryContact" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.updatePrimaryContact(UpdatePrimaryContact(
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
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldContact shouldBe None
      response.newContact shouldBe Some(
        Contact(
          firstName = "firstName1",
          lastName = "lastName1",
          emailAddress = Some("test1@test.com"),
          phone = Some("111-111-1112"),
          userName = "contactUsername1"
        )
      )
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process UpdateAddress" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.updateAddress(UpdateAddress(
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
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldAddress shouldBe None
      response.newAddress shouldBe Some(
        Address(
          line1 = "line3",
          line2 = "line4",
          city = "city1",
          stateProvince = "stateProvince1",
          country = "country1",
          postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
        )
      )
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process AddOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.addOrganizations(AddOrganizations(
        tenantId = Some(TenantId(tenantId)),
        orgId = Seq(OrganizationId("org1")),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.newOrgsList shouldBe Seq(OrganizationId("org1"))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process RemoveOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.addOrganizations(AddOrganizations(
        tenantId = Some(TenantId(tenantId)),
        orgId = Seq(OrganizationId("org1")),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      response.newOrgsList shouldBe Seq(OrganizationId("org1"))

      val removeResponse = client.removeOrganizations(RemoveOrganizations(
        tenantId = Some(TenantId(tenantId)),
        orgId = Seq(OrganizationId("org1")),
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      removeResponse.tenantId shouldBe Some(TenantId(tenantId))
      removeResponse.newOrgsList shouldBe Seq.empty
      removeResponse.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process ActivateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val updateTenantNameResponse = client.updateTenantName(UpdateTenantName(
        tenantId = Some(TenantId(tenantId)),
        newName = "newName",
        updatingUser = Some(MemberId("updatingUser"))
      )).futureValue

      updateTenantNameResponse.tenantId shouldBe Some(TenantId(tenantId))
      updateTenantNameResponse.newName shouldBe "newName"

      val updatePrimaryContactResponse = client.updatePrimaryContact(UpdatePrimaryContact(
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
      )).futureValue

      updatePrimaryContactResponse.tenantId shouldBe Some(TenantId(tenantId))
      updatePrimaryContactResponse.newContact shouldBe Some(
        Contact(
          firstName = "firstName1",
          lastName = "lastName1",
          emailAddress = Some("test1@test.com"),
          phone = Some("111-111-1112"),
          userName = "contactUsername1"
        )
      )

      val updateAddressResponse = client.updateAddress(UpdateAddress(
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
      )).futureValue

      updateAddressResponse.tenantId shouldBe Some(TenantId(tenantId))
      updateAddressResponse.newAddress shouldBe Some(
        Address(
          line1 = "line3",
          line2 = "line4",
          city = "city1",
          stateProvince = "stateProvince1",
          country = "country1",
          postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
        )
      )

      val response = client.activateTenant(ActivateTenant(
        tenantId = Some(TenantId(tenantId)),
        activatingUser = Some(MemberId("activatingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("activatingUser"))
    }
  }

  it should "properly process SuspendTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser"))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.suspendTenant(SuspendTenant(
        tenantId = Some(TenantId(tenantId)),
        suspensionReason = "reason",
        suspendingUser = Some(MemberId("suspendingUser"))
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))
    }
  }
}