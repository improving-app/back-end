package com.improving.app.organization

import akka.grpc.{GrpcClientSettings, GrpcServiceException}
import com.dimafeng.testcontainers.DockerComposeContainer
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.organization.TestData.baseOrganizationInfo
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

  behavior.of("TestServer in a test container")

  override def afterAll(): Unit = {
    system.terminate()
  }
  it should "expose a port for organization-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "gracefully handle bad requests that fail at service level" taggedAs Retryable in {
    withContainers { containers => }
  }

  it should "gracefully handle bad requests that fail at entity level" taggedAs Retryable in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("establishingUser"),
            organizationInfo = baseOrganizationInfo
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe OrganizationId(organizationId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val response = client
        .activateOrganization(
          ActivateOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("activatingUser"),
          )
        )
        .futureValue

      response.organizationId shouldBe OrganizationId(organizationId)
      response.metaInfo.lastUpdatedBy shouldBe MemberId("activatingUser")

      val response2 = client
        .activateOrganization(
          ActivateOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("activatingUser"),
          )
        )

      Seq(("Invalid Activate after previously activating", response2)).foreach({
        case (clue: String, responseFuture: Future[_]) =>
          withClue(clue) {
            val exception = responseFuture.failed.futureValue
            exception shouldBe a[GrpcServiceException]
          }
      })
    }
  }

  it should "properly process ActivateOrganization" taggedAs Retryable in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("establishingUser"),
            organizationInfo = baseOrganizationInfo
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe OrganizationId(organizationId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val suspendedResponse = client
        .suspendOrganization(
          SuspendOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("suspendingUser"),
          )
        )
        .futureValue

      suspendedResponse.organizationId shouldBe OrganizationId(organizationId)
      suspendedResponse.metaInfo.lastUpdatedBy shouldBe MemberId("suspendingUser")

      val response = client
        .activateOrganization(
          ActivateOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("activatingUser"),
          )
        )
        .futureValue

      response.organizationId shouldBe OrganizationId(organizationId)
      response.metaInfo.lastUpdatedBy shouldBe MemberId("activatingUser")
    }
  }

  it should "properly process SuspendOrganization" in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("establishingUser"),
            organizationInfo = baseOrganizationInfo
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe OrganizationId(organizationId)
      establishedResponse.metaInfo.createdBy shouldBe MemberId("establishingUser")

      val response = client
        .suspendOrganization(
          SuspendOrganization(
            organizationId = OrganizationId(organizationId),
            onBehalfOf = MemberId("suspendingUser"),
          )
        )
        .futureValue

      response.organizationId shouldBe OrganizationId(organizationId)
      response.metaInfo.lastUpdatedBy shouldBe MemberId("suspendingUser")
    }
  }
}
