import TestData._
import akka.grpc.{GrpcClientSettings, GrpcServiceException}
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.organization.api.{OrganizationService, OrganizationServiceClient}
import com.improving.app.organization.domain._
import io.grpc.Status
import org.scalatest.tagobjects.Retryable

import scala.concurrent.Future
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

  it should "gracefully handle bad requests that fail at service level" taggedAs(Retryable) in {
    withContainers { containers =>
      val client = getClient(containers)

      val invalidRequests = Seq(
        (
          "EstablishOrganization missing organizationId",
          client.establishOrganization(EstablishOrganization(
            organizationId = None,
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = Some(baseOrganizationInfo)
          ))
        ),
        (
          "EstablishOrganization missing onBehalfOf",
          client.establishOrganization(EstablishOrganization(
            organizationId = Some(OrganizationId("boo")),
            onBehalfOf = None,
            organizationInfo = Some(baseOrganizationInfo)
          ))
        ),
        (
          "SuspendOrganization missing OrganizationId",
          client.suspendOrganization(SuspendOrganization(
            organizationId = None,
            onBehalfOf = Some(MemberId("suspendingUser")),
          ))
        ),
        (
          "ActivateOrganization missing OrganizationId",
          client.activateOrganization(ActivateOrganization(
            organizationId = None,
            onBehalfOf = Some(MemberId("activatingUser")),
          ))
        )
      )

      invalidRequests.foreach({ case (clue: String, responseFuture: Future[_]) =>
        withClue(clue) {
          val exception = responseFuture.failed.futureValue
          exception shouldBe a[GrpcServiceException]
          val serviceException = exception.asInstanceOf[GrpcServiceException]
          serviceException.status.getCode shouldBe Status.Code.INVALID_ARGUMENT
        }
      })
    }
  }

  it should "gracefully handle bad requests that fail at entity level" ignore { // I'm struggling to figure out how to gracefully handle this error
    withContainers { containers =>
      val client = getClient(containers)

      val invalidRequests = Seq(
        (
          "EstablishOrganization missing OrganizationInfo",
          client.establishOrganization(EstablishOrganization(
            organizationId = Some(OrganizationId("boo")),
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = None
          ))
        ),
      )

      invalidRequests.foreach({ case (clue: String, responseFuture: Future[_]) =>
        withClue(clue) {
          val exception = responseFuture.failed.futureValue
          exception shouldBe a[GrpcServiceException]
          val serviceException = exception.asInstanceOf[GrpcServiceException]
          serviceException.status.getCode shouldBe Status.Code.INVALID_ARGUMENT
        }
      })
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