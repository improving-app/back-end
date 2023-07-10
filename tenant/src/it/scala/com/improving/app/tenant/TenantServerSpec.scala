package com.improving.app.tenant

import akka.grpc.GrpcClientSettings
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.tenant.TestData.itBaseTenantInfo
import com.improving.app.tenant.api.{TenantService, TenantServiceClient}
import com.improving.app.tenant.domain._
import com.improving.app.tenant.domain.util.EditableInfoUtil
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
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.getTenantId shouldBe TenantId(tenantId)
      establishedResponse.getMetaInfo.getCreatedBy shouldBe MemberId("establishingUser")

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
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("updatingUser")),
            infoToUpdate = Some(updateInfo)
          )
        )
        .futureValue

      response.getTenantId shouldBe TenantId(tenantId)
      response.newInfo shouldBe updateInfo.toInfo
      response.getMetaInfo.getLastUpdatedBy shouldBe MemberId("updatingUser")
    }
  }

  it should "properly process ActivateTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.getTenantId shouldBe TenantId(tenantId)
      establishedResponse.getMetaInfo.getCreatedBy shouldBe MemberId("establishingUser")

      val suspendResponse = client
        .suspendTenant(
          SuspendTenant(
            tenantId = Some(TenantId(tenantId)),
            suspensionReason = "reason",
            onBehalfOf = Some(MemberId("suspendingUser"))
          )
        )
        .futureValue

      suspendResponse.getTenantId shouldBe TenantId(tenantId)
      suspendResponse.getMetaInfo.getLastUpdatedBy shouldBe MemberId("suspendingUser")

      val response = client
        .activateTenant(
          ActivateTenant(
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("activatingUser"))
          )
        )
        .futureValue

      response.getTenantId shouldBe TenantId(tenantId)
      response.getMetaInfo.getLastUpdatedBy shouldBe MemberId("activatingUser")
    }
  }

  it should "properly process SuspendTenant" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.getTenantId shouldBe TenantId(tenantId)
      establishedResponse.getMetaInfo.getCreatedBy shouldBe MemberId("establishingUser")

      val response = client
        .suspendTenant(
          SuspendTenant(
            tenantId = Some(TenantId(tenantId)),
            suspensionReason = "reason",
            onBehalfOf = Some(MemberId("suspendingUser"))
          )
        )
        .futureValue

      response.getTenantId shouldBe TenantId(tenantId)
      response.getMetaInfo.getLastUpdatedBy shouldBe MemberId("suspendingUser")
    }
  }

  it should "properly process GetOrganizations" in {
    withContainers { containers =>
      val client = getClient(containers)

      val tenantId = Random.nextString(31)

      val establishedResponse = client
        .establishTenant(
          EstablishTenant(
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("establishingUser")),
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

      establishedResponse.getTenantId shouldBe TenantId(tenantId)
      establishedResponse.getMetaInfo.getCreatedBy shouldBe MemberId("establishingUser")

      val response = client
        .getOrganizations(
          GetOrganizations(
            tenantId = Some(TenantId(tenantId)),
          )
        )
        .futureValue

      response.getOrganizations shouldBe TenantOrganizationList(
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
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            tenantInfo = Some(itBaseTenantInfo)
          )
        )
        .futureValue

      establishedResponse.getTenantId shouldBe TenantId(tenantId)
      establishedResponse.getMetaInfo.getCreatedBy shouldBe MemberId("establishingUser")

      val response = client
        .terminateTenant(
          TerminateTenant(
            tenantId = Some(TenantId(tenantId)),
            onBehalfOf = Some(MemberId("terminatingUser"))
          )
        )
        .futureValue
      response.getTenantId shouldBe TenantId(tenantId)
      response.metaInfo.map(_.getLastUpdatedBy) shouldBe MemberId("terminatingUser")
    }
  }
}
