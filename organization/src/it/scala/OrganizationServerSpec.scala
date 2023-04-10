import TestData._
import akka.grpc.GrpcClientSettings
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.organization.api.{OrganizationService, OrganizationServiceClient}
import com.improving.app.organization.domain._

import scala.util.Random

class OrganizationServerSpec extends ServiceTestContainerSpec(8082, "organization-service") {
  private def getClient(containers: Containers): OrganizationService = {
    val (host, port) = getContainerHostPort(containers)
    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    OrganizationServiceClient(clientSettings)
  }

  behavior of "TestServer in a test container"

  it should "expose a port for organization-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "properly process ActivateOrganization" in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client.establishOrganization(EstablishOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("establishingUser")),
        organizationInfo = Some(baseOrganizationInfo)
      )).futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val suspendedResponse = client.suspendOrganization(SuspendOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("suspendingUser")),
      )).futureValue

      suspendedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      suspendedResponse.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))

      val response = client.activateOrganization(ActivateOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("activatingUser")),
      )).futureValue

      response.organizationId shouldBe Some(OrganizationId(organizationId))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("activatingUser"))
    }
  }

  it should "properly process SuspendOrganization" in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client.establishOrganization(EstablishOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("establishingUser")),
        organizationInfo = Some(baseOrganizationInfo)
      )).futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.metaInfo.get.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client.suspendOrganization(SuspendOrganization(
        organizationId = Some(OrganizationId(organizationId)),
        onBehalfOf = Some(MemberId("suspendingUser")),
      )).futureValue

      response.organizationId shouldBe Some(OrganizationId(organizationId))
      response.metaInfo.get.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))
    }
  }
}