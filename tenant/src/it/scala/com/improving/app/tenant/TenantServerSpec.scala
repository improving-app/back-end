package com.improving.app.tenant

import akka.grpc.GrpcClientSettings
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.tenant.TestData.itBaseTenantInfo
import com.improving.app.tenant.api.{TenantService, TenantServiceClient}
import com.improving.app.tenant.domain._
import com.improving.app.tenant.domain.util.infoFromEditableInfo
import org.scalatest.tagobjects.Retryable

import scala.util.Random

class TenantServerSpec extends ServiceTestContainerSpec(8080, "tenant-service") {
  private def getClient(containers: Containers): TenantService = {
    val (host, port) = getContainerHostPort(containers)
    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    TenantServiceClient(clientSettings)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }
  behavior.of("TestServer in a test container")

  it should "expose a port for tenant-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "properly process EditInfo" taggedAs Retryable in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = TenantId(tenantId),
            establishingUser = MemberId("establishingUser"),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.tenantId shouldBe TenantId(tenantId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val newName = "newName"

      val newAddress =
        EditableAddress(
          line1 = Some("line3"),
          line2 = Some("line4"),
          city = Some("city1"),
          stateProvince = Some("stateProvince1"),
          country = Some("country1"),
          postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode1")))
        )

      val newContact = EditableContact(
        firstName = Some("firstName1"),
        lastName = Some("lastName1"),
        emailAddress = Some("test1@test.com"),
        phone = Some("111-111-1112"),
        userName = Some("contactUsername1")
      )

      val newOrgs = TenantOrganizationList(
        Seq(
          OrganizationId("a"),
          OrganizationId("b")
        )
      )

      val updateInfo: EditableTenantInfo = EditableTenantInfo(
        name = Some(newName),
        address = Some(newAddress),
        primaryContact = Some(newContact),
        organizations = Some(newOrgs)
      )

      val response = client
        .editInfo(
          EditInfo(
            tenantId = TenantId(tenantId),
            editingUser = MemberId("updatingUser"),
            infoToUpdate = updateInfo
          )
        )
        .futureValue

      response.tenantId shouldBe TenantId(tenantId)
      response.oldInfo shouldBe itBaseTenantInfo
      response.newInfo shouldBe infoFromEditableInfo(updateInfo)
      response.metaInfo.lastUpdatedBy shouldBe MemberId("updatingUser")
    }
  }

  it should "properly process ActivateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = TenantId(tenantId),
            establishingUser = MemberId("establishingUser"),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.tenantId shouldBe TenantId(tenantId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val suspendResponse = client
        .suspendTenant(
          SuspendTenant(
            tenantId = TenantId(tenantId),
            suspensionReason = "reason",
            suspendingUser = MemberId("suspendingUser")
          )
        )
        .futureValue

      suspendResponse.tenantId shouldBe TenantId(tenantId)
      suspendResponse.metaInfo.lastUpdatedBy shouldBe MemberId("suspendingUser")

      val response = client
        .activateTenant(
          ActivateTenant(
            tenantId = TenantId(tenantId),
            activatingUser = MemberId("activatingUser")
          )
        )
        .futureValue

      response.tenantId shouldBe TenantId(tenantId)
      response.metaInfo.lastUpdatedBy shouldBe MemberId("activatingUser")
    }
  }

  it should "properly process SuspendTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = TenantId(tenantId),
            establishingUser = MemberId("establishingUser"),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.tenantId shouldBe TenantId(tenantId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val response = client
        .suspendTenant(
          SuspendTenant(
            tenantId = TenantId(tenantId),
            suspensionReason = "reason",
            suspendingUser = MemberId("suspendingUser")
          )
        )
        .futureValue

      response.tenantId shouldBe TenantId(tenantId)
      response.metaInfo.lastUpdatedBy shouldBe MemberId("suspendingUser")
    }
  }

  it should "properly process GetOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = TenantId(tenantId),
            establishingUser = MemberId("establishingUser"),
            tenantInfo = Some(
              itBaseTenantInfo.copy(organizations =
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
        )
        .futureValue

      establishedResponse.tenantId shouldBe TenantId(tenantId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val response = client
        .getOrganizations(
          GetOrganizations(
            tenantId = TenantId(tenantId),
          )
        )
        .futureValue

      response.organizations shouldBe TenantOrganizationList(
        Seq(
          OrganizationId("org1"),
          OrganizationId("org2")
        )
      )
    }
  }

  it should "properly process TerminateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = TenantId(tenantId),
            establishingUser = MemberId("establishingUser"),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.tenantId shouldBe TenantId(tenantId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val response = client
        .terminateTenant(
          TerminateTenant(
            tenantId = TenantId(tenantId),
            terminatingUser = MemberId("terminatingUser")
          )
        )
        .futureValue
      response.tenantId.id shouldBe tenantId
      response.metaInfo.lastUpdatedBy.id shouldBe "terminatingUser"
    }
  }
}
