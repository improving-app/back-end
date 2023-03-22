package com.improving.app.organization.api

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import com.improving.app.organization.{
  ActivateOrganizationRequest,
  AddMembersToOrganizationRequest,
  AddOwnersToOrganizationRequest,
  EditOrganizationInfoRequest,
  GetOrganizationByIdRequest,
  GetOrganizationInfoRequest,
  OrganizationServiceImpl,
  OrganizationStatus,
  ReleaseOrganizationRequest,
  RemoveMembersFromOrganizationRequest,
  RemoveOwnersFromOrganizationRequest,
  SuspendOrganizationRequest,
  TerminateOrganizationRequest,
  UpdateOrganizationAccountsRequest,
  UpdateOrganizationContactsRequest,
  UpdateOrganizationStatusRequest,
  UpdateParentRequest
}
import com.improving.app.organization.utils.{CassandraTestContainer, LoanedActorSystem}
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import com.improving.app.organization.TestData._

@DoNotDiscover
class OrganizationServiceIntegrationSpec
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
    "OrganizationServiceIntegrationSpec"
  )

  override val cassandraInitScriptPath: String =
    "organization/src/test/resources/cassandra-init.cql"

  "OrganizationService" should {

    val organizationService = new OrganizationServiceImpl()(system.toTyped)

    "handle grpc calls for establish Organization" in {

      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      response.orgId.isDefined shouldBe true
    }

    "should return Organization with the correct id" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testInfo)
      organization.members shouldBe testMembers
      organization.owners shouldBe testOwners
      organization.contacts shouldBe testContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember)
    }

    "should return OrganizationInfo with the correct id" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val getOrganizationInfoRequest = GetOrganizationInfoRequest(orgId)

      val organizationInfo =
        organizationService
          .getOrganizationInfo(getOrganizationInfoRequest)
          .futureValue

      organizationInfo.info shouldBe Some(testInfo)
    }

    "should add members to Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val addMembersToOrganizationRequest =
        AddMembersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      val membersAddedToOrganization =
        organizationService
          .addMembersToOrganization(addMembersToOrganizationRequest)
          .futureValue

      membersAddedToOrganization.newMembers shouldBe newMembers

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testInfo)
      organization.members shouldBe testMembers ++ newMembers
      organization.owners shouldBe testOwners
      organization.contacts shouldBe testContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
    }

    "should remove members from Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val addMembersToOrganizationRequest =
        AddMembersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      val membersAddedToOrganization =
        organizationService
          .addMembersToOrganization(addMembersToOrganizationRequest)
          .futureValue

      membersAddedToOrganization.newMembers shouldBe newMembers

      val removeMembersFromOrganizationRequest =
        RemoveMembersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      val membersRemovedFromOrganization = organizationService
        .removeMembersFromOrganization(removeMembersFromOrganizationRequest)
        .futureValue

      membersRemovedFromOrganization.removedMembers shouldBe newMembers

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testInfo)
      organization.members shouldBe testMembers
      organization.owners shouldBe testOwners
      organization.contacts shouldBe testContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember3)
    }

    "should add owners to Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val addOwnersToOrganizationRequest =
        AddOwnersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      val ownersAddedToOrganization =
        organizationService
          .addOwnersToOrganization(addOwnersToOrganizationRequest)
          .futureValue

      ownersAddedToOrganization.newOwners shouldBe newMembers

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testInfo)
      organization.members shouldBe testMembers
      organization.owners shouldBe testOwners ++ newMembers
      organization.contacts shouldBe testContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
    }

    "should remove owners from Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val addOwnersToOrganizationRequest =
        AddOwnersToOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember2)
        )

      val ownersAddedToOrganization =
        organizationService
          .addOwnersToOrganization(addOwnersToOrganizationRequest)
          .futureValue

      ownersAddedToOrganization.newOwners shouldBe newMembers

      val removeOwnersFromOrganizationRequest =
        RemoveOwnersFromOrganizationRequest(
          orgId,
          newMembers,
          Some(testActingMember3)
        )

      val ownersRemovedFromOrganization = organizationService
        .removeOwnersFromOrganization(removeOwnersFromOrganizationRequest)
        .futureValue

      ownersRemovedFromOrganization.removedOwners shouldBe newMembers

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testInfo)
      organization.members shouldBe testMembers
      organization.owners shouldBe testOwners
      organization.contacts shouldBe testContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember3)
    }

    "should update info in Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val editOrganizationInfoRequest =
        EditOrganizationInfoRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      val organizationInfoUpdated =
        organizationService
          .editOrganizationInfo(editOrganizationInfoRequest)
          .futureValue

      organizationInfoUpdated.info shouldBe Some(testNewTestInfo)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testNewTestInfo)
      organization.members shouldBe testMembers
      organization.owners shouldBe testOwners
      organization.contacts shouldBe testContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)

      val editParialOrganizationInfoRequest =
        EditOrganizationInfoRequest(
          orgId,
          Some(testNewPartialTestInfo),
          Some(testActingMember3)
        )

      val organizationPartialInfoUpdated =
        organizationService
          .editOrganizationInfo(editParialOrganizationInfoRequest)
          .futureValue

      organizationPartialInfoUpdated.info shouldBe defined

      val organization1 =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization1.meta.isDefined shouldBe true
      organization1.info.flatMap(_.url) shouldBe testNewPartialTestInfo.url
      organization1.info.flatMap(_.logo) shouldBe testNewPartialTestInfo.logo
      organization1.info.map(_.name) shouldBe Some(testNewPartialTestInfo.name)
      organization1.info.map(_.isPrivate) shouldBe Some(
        testNewPartialTestInfo.isPrivate
      )
      organization1.info.flatMap(_.shortName) shouldBe testNewTestInfo.shortName
      organization1.info.flatMap(_.address) shouldBe testNewTestInfo.address
      organization1.members shouldBe testMembers
      organization1.owners shouldBe testOwners
      organization1.contacts shouldBe testContacts
      organization1.getMeta.lastUpdatedBy shouldBe Some(testActingMember3)
    }

    "should release Organization correctly" in {
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

      val releasedOrganization =
        organizationService
          .releaseOrganization(releaseOrganizationRequest)
          .futureValue

      releasedOrganization.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.status shouldBe OrganizationStatus.ORGANIZATION_STATUS_RELEASED
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
      organization.getMeta.currentStatus shouldBe OrganizationStatus.ORGANIZATION_STATUS_RELEASED
    }

    "should activate and suspend Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val activateOrganizationRequest =
        ActivateOrganizationRequest(
          orgId,
          Some(testActingMember2)
        )

      val activatedOrganization =
        organizationService
          .activateOrganization(activateOrganizationRequest)
          .futureValue

      activatedOrganization.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.status shouldBe OrganizationStatus.ORGANIZATION_STATUS_ACTIVE
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
      organization.getMeta.currentStatus shouldBe OrganizationStatus.ORGANIZATION_STATUS_ACTIVE

      val suspendOrganizationRequest =
        SuspendOrganizationRequest(orgId, Some(testActingMember3))

      val suspenedOrganization =
        organizationService
          .suspendOrganization(suspendOrganizationRequest)
          .futureValue

      suspenedOrganization.actingMember shouldBe Some(testActingMember3)

      val organization1 =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization1.meta.isDefined shouldBe true
      organization1.status shouldBe OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED
      organization1.getMeta.lastUpdatedBy shouldBe Some(testActingMember3)
      organization1.getMeta.currentStatus shouldBe OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED
    }

    "should terminate Organization correctly" in {
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

      val terminatedOrganization =
        organizationService
          .terminateOrganization(terminateOrganizationRequest)
          .futureValue

      terminatedOrganization.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.status shouldBe OrganizationStatus.ORGANIZATION_STATUS_TERMINATED
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
      organization.getMeta.currentStatus shouldBe OrganizationStatus.ORGANIZATION_STATUS_TERMINATED
    }

    "should update parent in Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val updateParentRequest =
        UpdateParentRequest(
          orgId,
          Some(testNewParent),
          Some(testActingMember2)
        )

      val updatedParentOrganization =
        organizationService
          .updateParent(updateParentRequest)
          .futureValue

      updatedParentOrganization.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.parent shouldBe Some(testNewParent)
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
    }

    "should update status in Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val updateOrganizationStatusRequest =
        UpdateOrganizationStatusRequest(
          orgId,
          OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
          Some(testActingMember2)
        )

      val organizationStatusUpdated =
        organizationService
          .updateOrganizationStatus(updateOrganizationStatusRequest)
          .futureValue

      organizationStatusUpdated.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.status shouldBe OrganizationStatus.ORGANIZATION_STATUS_ACTIVE
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
      organization.getMeta.currentStatus shouldBe OrganizationStatus.ORGANIZATION_STATUS_ACTIVE
    }

    "should update contacts in Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val updateOrganizationContactsRequest =
        UpdateOrganizationContactsRequest(
          orgId,
          testNewContacts,
          Some(testActingMember2)
        )

      val organizationAccountsUpdated =
        organizationService
          .updateOrganizationContacts(updateOrganizationContactsRequest)
          .futureValue

      organizationAccountsUpdated.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.contacts shouldBe testNewContacts
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
    }

    "should update accounts in Organization correctly" in {
      val response =
        organizationService
          .establishOrganization(establishOrganizationRequest)
          .futureValue

      val orgId = response.orgId

      orgId.isDefined shouldBe true

      val updateOrganizationAccountsRequest =
        UpdateOrganizationAccountsRequest(
          orgId,
          Some(testNewTestInfo),
          Some(testActingMember2)
        )

      val organizationAccountsUpdated =
        organizationService
          .updateOrganizationAccounts(updateOrganizationAccountsRequest)
          .futureValue

      organizationAccountsUpdated.actingMember shouldBe Some(testActingMember2)

      val getOrganizationByIdRequest = GetOrganizationByIdRequest(orgId)

      val organization =
        organizationService
          .getOrganization(getOrganizationByIdRequest)
          .futureValue

      organization.meta.isDefined shouldBe true
      organization.info shouldBe Some(testNewTestInfo)
      organization.getMeta.lastUpdatedBy shouldBe Some(testActingMember2)
    }
  }
}
