package com.improving.app.organization.api

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import com.improving.app.organization._
import com.improving.app.organization.utils.{CassandraTestContainer, LoanedActorSystem}
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import com.improving.app.organization.TestData._
import org.scalatest.exceptions.TestFailedException

@DoNotDiscover
class OrganizationServiceIntegrationFailSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers
    with CassandraTestContainer
    with LoanedActorSystem {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(5, Seconds)),
      interval = scaled(Span(100, Milliseconds))
    )

  implicit private val system: ActorSystem = ActorSystem(
    "OrganizationServiceIntegrationFailSpec"
  )

  override val cassandraInitScriptPath: String =
    "organization/src/test/resources/cassandra-init.cql"

  "OrganizationService" should {

    val organizationService = new OrganizationServiceImpl()(system.toTyped)

    "fail when operate on terminated Organization" in {

      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val terminateOrganizationRequest =
        TerminateOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      organizationService
        .terminateOrganization(terminateOrganizationRequest)
        .futureValue

      val suspendOrganizationRequest =
        SuspendOrganizationRequest(orgId, Some(testActingMember3))

      intercept[TestFailedException](
        organizationService
          .suspendOrganization(suspendOrganizationRequest)
          .futureValue
      )

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      intercept[TestFailedException](
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue
      )

      val getOrganizationInfoRequest = GetOrganizationInfoRequest(orgId)

      intercept[TestFailedException](
        organizationService
          .getOrganizationInfo(getOrganizationInfoRequest)
          .futureValue
      )

      val addMembersToOrganizationRequest =
        AddMembersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .addMembersToOrganization(addMembersToOrganizationRequest)
          .futureValue
      )

      val removeMembersFromOrganizationRequest =
        RemoveMembersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      intercept[TestFailedException](
        organizationService
          .removeMembersFromOrganization(removeMembersFromOrganizationRequest)
          .futureValue
      )

      val addOwnersToOrganizationRequest =
        AddOwnersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .addOwnersToOrganization(addOwnersToOrganizationRequest)
          .futureValue
      )

      val removeOwnersFromOrganizationRequest =
        RemoveOwnersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      intercept[TestFailedException](
        organizationService
          .removeOwnersFromOrganization(removeOwnersFromOrganizationRequest)
          .futureValue
      )

      val editOrganizationInfoRequest =
        EditOrganizationInfoRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .editOrganizationInfo(editOrganizationInfoRequest)
          .futureValue
      )

      val activateOrganizationRequest =
        ActivateOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .activateOrganization(activateOrganizationRequest)
          .futureValue
      )

      val updateParentRequest =
        UpdateParentRequest(
          orgId,
          Some(testNewParent),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateParent(updateParentRequest)
          .futureValue
      )

      val updateOrganizationStatusRequest =
        UpdateOrganizationStatusRequest(
          orgId,
          OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationStatus(updateOrganizationStatusRequest)
          .futureValue
      )

      val updateOrganizationContactsRequest =
        UpdateOrganizationContactsRequest(
          orgId,
          testNewContacts,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationContacts(updateOrganizationContactsRequest)
          .futureValue
      )

      val updateOrganizationAccountsRequest =
        UpdateOrganizationAccountsRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationAccounts(updateOrganizationAccountsRequest)
          .futureValue
      )

      intercept[TestFailedException](
        organizationService
          .terminateOrganization(terminateOrganizationRequest)
          .futureValue
      )
    }

    "fail when operate on released Organization" in {

      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val releaseOrganizationRequest =
        ReleaseOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      organizationService
        .releaseOrganization(releaseOrganizationRequest)
        .futureValue

      val suspendOrganizationRequest =
        SuspendOrganizationRequest(orgId, Some(testActingMember3))

      intercept[TestFailedException](
        organizationService
          .suspendOrganization(suspendOrganizationRequest)
          .futureValue
      )

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      intercept[TestFailedException](
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue
      )

      val getOrganizationInfoRequest = GetOrganizationInfoRequest(orgId)

      intercept[TestFailedException](
        organizationService
          .getOrganizationInfo(getOrganizationInfoRequest)
          .futureValue
      )

      val addMembersToOrganizationRequest =
        AddMembersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .addMembersToOrganization(addMembersToOrganizationRequest)
          .futureValue
      )

      val removeMembersFromOrganizationRequest =
        RemoveMembersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      intercept[TestFailedException](
        organizationService
          .removeMembersFromOrganization(removeMembersFromOrganizationRequest)
          .futureValue
      )

      val addOwnersToOrganizationRequest =
        AddOwnersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .addOwnersToOrganization(addOwnersToOrganizationRequest)
          .futureValue
      )

      val removeOwnersFromOrganizationRequest =
        RemoveOwnersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      intercept[TestFailedException](
        organizationService
          .removeOwnersFromOrganization(removeOwnersFromOrganizationRequest)
          .futureValue
      )

      val editOrganizationInfoRequest =
        EditOrganizationInfoRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .editOrganizationInfo(editOrganizationInfoRequest)
          .futureValue
      )

      val activateOrganizationRequest =
        ActivateOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .activateOrganization(activateOrganizationRequest)
          .futureValue
      )

      val updateParentRequest =
        UpdateParentRequest(
          orgId,
          Some(testNewParent),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateParent(updateParentRequest)
          .futureValue
      )

      val updateOrganizationStatusRequest =
        UpdateOrganizationStatusRequest(
          orgId,
          OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationStatus(updateOrganizationStatusRequest)
          .futureValue
      )

      val updateOrganizationContactsRequest =
        UpdateOrganizationContactsRequest(
          orgId,
          testNewContacts,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationContacts(updateOrganizationContactsRequest)
          .futureValue
      )

      val updateOrganizationAccountsRequest =
        UpdateOrganizationAccountsRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationAccounts(updateOrganizationAccountsRequest)
          .futureValue
      )

      val terminateOrganizationRequest =
        TerminateOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .terminateOrganization(terminateOrganizationRequest)
          .futureValue
      )

      intercept[TestFailedException](
        organizationService
          .releaseOrganization(releaseOrganizationRequest)
          .futureValue
      )
    }

    "fail when operate on suspended Organization" in {

      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val suspendOrganizationRequest =
        SuspendOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      organizationService
        .suspendOrganization(suspendOrganizationRequest)
        .futureValue

      intercept[TestFailedException](
        organizationService
          .suspendOrganization(suspendOrganizationRequest)
          .futureValue
      )

      val addMembersToOrganizationRequest =
        AddMembersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .addMembersToOrganization(addMembersToOrganizationRequest)
          .futureValue
      )

      val removeMembersFromOrganizationRequest =
        RemoveMembersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      intercept[TestFailedException](
        organizationService
          .removeMembersFromOrganization(removeMembersFromOrganizationRequest)
          .futureValue
      )

      val addOwnersToOrganizationRequest =
        AddOwnersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .addOwnersToOrganization(addOwnersToOrganizationRequest)
          .futureValue
      )

      val removeOwnersFromOrganizationRequest =
        RemoveOwnersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      intercept[TestFailedException](
        organizationService
          .removeOwnersFromOrganization(removeOwnersFromOrganizationRequest)
          .futureValue
      )

      val editOrganizationInfoRequest =
        EditOrganizationInfoRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .editOrganizationInfo(editOrganizationInfoRequest)
          .futureValue
      )

      val updateParentRequest =
        UpdateParentRequest(
          orgId,
          Some(testNewParent),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateParent(updateParentRequest)
          .futureValue
      )

      val updateOrganizationStatusRequest =
        UpdateOrganizationStatusRequest(
          orgId,
          OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationStatus(updateOrganizationStatusRequest)
          .futureValue
      )

      val updateOrganizationContactsRequest =
        UpdateOrganizationContactsRequest(
          orgId,
          testNewContacts,
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationContacts(updateOrganizationContactsRequest)
          .futureValue
      )

      val updateOrganizationAccountsRequest =
        UpdateOrganizationAccountsRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      intercept[TestFailedException](
        organizationService
          .updateOrganizationAccounts(updateOrganizationAccountsRequest)
          .futureValue
      )
    }
  }
}
