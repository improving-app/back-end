package com.improving.app.organization

import akka.grpc.{GrpcClientSettings, GrpcServiceException}
import com.improving.app.common.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.organization.TestData.{baseContact, baseOrganizationInfo, organizationInfoFromEditableInfo}
import com.improving.app.organization.api.{OrganizationService, OrganizationServiceClient}
import com.improving.app.organization.domain._
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
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = Some(baseOrganizationInfo)
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.getMetaInfo.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client
        .activateOrganization(
          ActivateOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("activatingUser")),
          )
        )
        .futureValue

      response.organizationId shouldBe Some(OrganizationId(organizationId))
      response.getMetaInfo.lastUpdatedBy shouldBe Some(MemberId("activatingUser"))

      val response2 = client
        .activateOrganization(
          ActivateOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("activatingUser")),
          )
        )

      Seq(("Invalid Activate after previously activating", response2)).foreach {
        case (clue: String, responseFuture: Future[_]) =>
          withClue(clue) {
            val exception = responseFuture.failed.futureValue
            exception shouldBe a[GrpcServiceException]
          }
      }
    }
  }

  it should "properly process ActivateOrganization" taggedAs Retryable in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = Some(baseOrganizationInfo)
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.getMetaInfo.createdBy shouldBe Some(MemberId("establishingUser"))

      val suspendedResponse = client
        .suspendOrganization(
          SuspendOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("suspendingUser")),
          )
        )
        .futureValue

      suspendedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      suspendedResponse.getMetaInfo.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))

      val response = client
        .activateOrganization(
          ActivateOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("activatingUser")),
          )
        )
        .futureValue

      response.organizationId shouldBe Some(OrganizationId(organizationId))
      response.getMetaInfo.lastUpdatedBy shouldBe Some(MemberId("activatingUser"))
    }
  }

  it should "properly process SuspendOrganization" in {
    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = Some(baseOrganizationInfo)
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.getMetaInfo.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client
        .suspendOrganization(
          SuspendOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("suspendingUser")),
          )
        )
        .futureValue

      response.organizationId shouldBe Some(OrganizationId(organizationId))
      response.getMetaInfo.lastUpdatedBy shouldBe Some(MemberId("suspendingUser"))
    }
  }

  it should "properly process GetOrganizationInfo" in {

    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = Some(baseOrganizationInfo)
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.getMetaInfo.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client
        .getOrganizationInfo(
          GetOrganizationInfo(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("suspendingUser")),
          )
        )
        .futureValue

      response shouldEqual organizationInfoFromEditableInfo(baseOrganizationInfo)
    }
  }

  it should "properly process UpdateOrganizationContacts and GetOrganizationContacts" in {

    withContainers { containers =>
      val client = getClient(containers)

      val organizationId = Random.nextString(31)

      val establishedResponse = client
        .establishOrganization(
          EstablishOrganization(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("establishingUser")),
            organizationInfo = Some(baseOrganizationInfo)
          )
        )
        .futureValue

      establishedResponse.organizationId shouldBe Some(OrganizationId(organizationId))
      establishedResponse.getMetaInfo.createdBy shouldBe Some(MemberId("establishingUser"))

      val response = client
        .updateOrganizationContacts(
          UpdateOrganizationContacts(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("updatingUser)")),
            contacts = Seq(baseContact)
          )
        )
        .futureValue

      response.contacts shouldEqual Seq(baseContact)

      val queryResponse = client
        .getOrganizationContacts(
          GetOrganizationContacts(
            organizationId = Some(OrganizationId(organizationId)),
            onBehalfOf = Some(MemberId("queryingUser")),
          )
        )
        .futureValue

      queryResponse shouldEqual ContactList(Seq(baseContact))
    }
  }

}
