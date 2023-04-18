import TestData._
import akka.grpc.GrpcClientSettings
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.tenant.api.{TenantService, TenantServiceClient}
import com.improving.app.tenant.domain._
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

  it should "properly process EditInfo" taggedAs(Retryable) in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser")),
        tenantInfo = Some(baseTenantInfo)
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val newName = "newName"

      val newAddress =
        Address(
          line1 = "line3",
          line2 = "line4",
          city = "city1",
          stateProvince = "stateProvince1",
          country = "country1",
          postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
        )

      val newContact = Contact(
        firstName = "firstName1",
        lastName = "lastName1",
        emailAddress = Some("test1@test.com"),
        phone = Some("111-111-1112"),
        userName = "contactUsername1"
      )

      val newOrgs = TenantOrganizationList(Seq(
        OrganizationId("a"),
        OrganizationId("b")
      ))

      val updateInfo = TenantInfo(
        name = newName,
        address = Some(newAddress),
        primaryContact = Some(newContact),
        organizations = Some(newOrgs)
      )

      val response = client.editInfo(EditInfo(
        tenantId = Some(TenantId(tenantId)),
        editingUser = Some(MemberId("updatingUser")),
        infoToUpdate = Some(updateInfo)
      )).futureValue

      response.tenantId shouldBe Some(TenantId(tenantId))
      response.oldInfo shouldBe Some(baseTenantInfo)
      response.newInfo shouldBe Some(updateInfo)
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("updatingUser"))
    }
  }

  it should "properly process ActivateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser")),
        tenantInfo = Some(baseTenantInfo)
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val suspendResponse = client.suspendTenant(SuspendTenant(
        tenantId = Some(TenantId(tenantId)),
        suspensionReason = "reason",
        suspendingUser = Some(MemberId("suspendingUser"))
      )).futureValue

      suspendResponse.tenantId shouldBe Some(TenantId(tenantId))
      suspendResponse.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))

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
        establishingUser = Some(MemberId("establishingUser")),
        tenantInfo = Some(baseTenantInfo)
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

  it should "properly process GetOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser")),
        tenantInfo = Some(baseTenantInfo.copy(organizations = Some(
          TenantOrganizationList(
            Seq(
              OrganizationId("org1"),
              OrganizationId("org2")
            )
          )
        )))
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.getOrganizations(GetOrganizations(
        tenantId = Some(TenantId(tenantId)),
      )).futureValue

      response.organizations shouldBe Some(
        TenantOrganizationList(
          Seq(
            OrganizationId("org1"),
            OrganizationId("org2")
          )
        )
      )
    }
  }

  it should "properly process TerminateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client.establishTenant(EstablishTenant(
        tenantId = Some(TenantId(tenantId)),
        establishingUser = Some(MemberId("establishingUser")),
        tenantInfo = Some(baseTenantInfo)
      )).futureValue

      establishedResponse.tenantId shouldBe Some(TenantId(tenantId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.terminateTenant(TerminateTenant(
        tenantId = Some(TenantId(tenantId)),
        terminatingUser = Some(MemberId("terminatingUser"))
      )).futureValue
      response.tenantId.get.id shouldBe tenantId
      response.metaInfo.get.lastUpdatedBy.get.id shouldBe "terminatingUser"
    }
  }
}