package com.improving.app.organization.domain

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.organization.OrganizationStatus.{ORGANIZATION_STATUS_ACTIVE, ORGANIZATION_STATUS_DRAFT, ORGANIZATION_STATUS_SUSPENDED, ORGANIZATION_STATUS_TERMINATED}
import com.improving.app.organization.TestData.{activateOrganizationRequest, establishOrganizationRequest, _}
import com.improving.app.organization.{ActivateOrganizationRequest, AddMembersToOrganizationRequest, AddOwnersToOrganizationRequest, Contacts, DraftOrganizationState, EditOrganizationInfoRequest, EstablishOrganizationRequest, EstablishedOrganizationState, GetOrganizationByIdRequest, GetOrganizationInfoRequest, Info, InitialEmptyOrganizationState, MembersAddedToOrganization, MembersRemovedFromOrganization, MetaInfo, OptionalDraftInfo, OrganizationActivated, OrganizationContactsUpdated, OrganizationEstablished, OrganizationEventResponse, OrganizationInfo, OrganizationInfoEdited, OrganizationResponse, OrganizationState, OrganizationStatus, OrganizationSuspended, OrganizationTerminated, OwnersAddedToOrganization, OwnersRemovedFromOrganization, ParentUpdated, RemoveMembersFromOrganizationRequest, RemoveOwnersFromOrganizationRequest, RequiredDraftInfo, SuspendOrganizationRequest, TerminateOrganizationRequest, TerminatedOrganizationState, TestData, UpdateOrganizationContactsRequest, UpdateParentRequest}
import com.improving.app.organization.domain.Organization.OrganizationCommand
import com.typesafe.config.Config
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike


class OrganizationSpec extends ScalaTestWithActorTestKit(OrganizationSpec.config) with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  import com.improving.app.organization.domain.OrganizationStateOps._

  trait SetUp {
    val orgId: String = testOrgId.id
    val eventSourcedTestKit: EventSourcedBehaviorTestKit[OrganizationCommand, OrganizationResponse, OrganizationState] = EventSourcedBehaviorTestKit.create[OrganizationCommand, OrganizationResponse, OrganizationState](system, Organization(orgId), EventSourcedBehaviorTestKit.disabledSerializationSettings)
  }

  trait Established extends SetUp {
    val establishOrganizationRequest: EstablishOrganizationRequest = TestData.establishOrganizationRequest
    val establishedResult: EventSourcedBehaviorTestKit.CommandResultWithReply[OrganizationCommand, OrganizationResponse, OrganizationState, StatusReply[OrganizationResponse]] = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(establishOrganizationRequest, _))
  }

  trait ValidateNewEstablishedState {
    val oldState: EstablishedOrganizationState
    val defaultStatus: OrganizationStatus

    def validateNewState(sOrgId: Option[OrganizationId], maybeInfo: Option[Option[Info]], maybeParent: Option[Option[OrganizationId]], maybeMembers: Option[Seq[MemberId]], maybeOwners: Option[Seq[MemberId]], maybeContacts: Option[Seq[Contacts]], meta: Option[MetaInfo], actingMember: Option[MemberId], newStatus: OrganizationStatus = defaultStatus): Unit = {
      sOrgId shouldBe oldState.orgId
      maybeInfo.foreach(_ shouldBe oldState.info)
      maybeParent.foreach(_ shouldBe oldState.parent)
      maybeMembers.foreach(_ shouldBe oldState.members)
      maybeOwners.foreach(_ shouldBe oldState.owners)
      maybeContacts.foreach(_ shouldBe oldState.contacts)
      meta.map(_.currentStatus) shouldBe Some(newStatus)
      meta.flatMap(_.lastUpdatedBy) shouldBe actingMember
    }
  }

  trait Active extends Established with ValidateNewEstablishedState {
    val activateOrganizationRequest: ActivateOrganizationRequest = ActivateOrganizationRequest(
      Some(testOrgId),
      Some(testActingMember2)
    )
    val activatedResult: EventSourcedBehaviorTestKit.CommandResultWithReply[OrganizationCommand, OrganizationResponse, OrganizationState, StatusReply[OrganizationResponse]] = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(activateOrganizationRequest, _))
    override val oldState: EstablishedOrganizationState = activatedResult.state.asInstanceOf[EstablishedOrganizationState]
    override val defaultStatus: OrganizationStatus = ORGANIZATION_STATUS_ACTIVE
  }

  trait Suspended extends Active with ValidateNewEstablishedState {
    val suspendOrganizationRequest: SuspendOrganizationRequest = TestData.suspendOrganizationRequest
    val suspendedResult: EventSourcedBehaviorTestKit.CommandResultWithReply[OrganizationCommand, OrganizationResponse, OrganizationState, StatusReply[OrganizationResponse]] = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(suspendOrganizationRequest, _))

    override val oldState: EstablishedOrganizationState = suspendedResult.state.asInstanceOf[EstablishedOrganizationState]
    override val defaultStatus: OrganizationStatus = ORGANIZATION_STATUS_SUSPENDED
  }

  trait Terminated extends Suspended {
    val terminateOrganizationRequest: TerminateOrganizationRequest = TestData.terminateOrganizationRequest
    val terminatedResult: EventSourcedBehaviorTestKit.CommandResultWithReply[OrganizationCommand, OrganizationResponse, OrganizationState, StatusReply[OrganizationResponse]] = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(terminateOrganizationRequest, _))
  }

  "Organization service" when {
    "in initial state" should {
      "allow Organization to be established" in new SetUp {
        val request: EstablishOrganizationRequest = TestData.establishOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationEstablished]
        result.event shouldBe OrganizationEstablished(
          orgId = Some(OrganizationId(orgId)),
          info = request.info,
          parent = request.parent,
          members = request.members,
          owners = request.owners,
          contacts = request.contacts,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe Some(RequiredDraftInfo(
              name = request.info.flatMap(_.name),
              isPrivate = request.info.get.isPrivate.getOrElse(true),
              tenant = request.info.get.tenant,
              owners = request.owners
            ))
            optionalDraftInfo shouldBe Some(OptionalDraftInfo(
              shortName = request.info.flatMap(_.shortName),
              address = request.info.flatMap(_.address),
              url = request.info.flatMap(_.url),
              logo = request.info.flatMap(_.logo),
              parent = request.parent,
              contacts = request.contacts,
              members = request.members
            ))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.createdBy) shouldBe request.actingMember

          case _ => fail("Incorrect state")
        }
      }

      "Succeed on establish if optional data is missing" in new SetUp {
        val request: EstablishOrganizationRequest = establishOrganizationRequest.copy(info = establishOrganizationRequest.info.map(_.copy(shortName = None, address = None, url = None, logo = None)), parent = None, contacts = Seq.empty[Contacts], members = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationEstablished]
        result.event shouldBe OrganizationEstablished(
          orgId = Some(OrganizationId(orgId)),
          info = request.info,
          parent = None,
          members = request.members,
          owners = request.owners,
          contacts = request.contacts,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe Some(RequiredDraftInfo(
              name = establishOrganizationRequest.info.flatMap(_.name),
              isPrivate = establishOrganizationRequest.info.get.isPrivate.getOrElse(true),
              tenant = establishOrganizationRequest.info.get.tenant,
              owners = establishOrganizationRequest.owners
            ))
            optionalDraftInfo shouldBe Some(OptionalDraftInfo(
              shortName = None,
              address = None,
              url = None,
              logo = None,
              parent = None,
              contacts = Seq.empty[Contacts]
            ))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.createdBy) shouldBe request.actingMember

          case _ => fail("Incorrect state")
        }
      }

      "Fail on establish if required data is missing" in new SetUp {
        val request: EstablishOrganizationRequest = establishOrganizationRequest.copy(info = establishOrganizationRequest.info.map(_.copy(name = None, tenant = None)), members = Seq.empty[MemberId], owners = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: name in info cannot be empty, tenant in info cannot be empty, owners cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe a[InitialEmptyOrganizationState]
      }

      "Fail on establish if info is empty" in new SetUp {
        val request: EstablishOrganizationRequest = establishOrganizationRequest.copy(info = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: info cannot be empty, name in info cannot be empty, tenant in info cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe a[InitialEmptyOrganizationState]
      }


      "Reject every other command" in new SetUp {
        everyMinimalRequest.filterNot(_.isInstanceOf[EstablishOrganizationRequest]).map(
          request => (request, eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _)))
        ).foreach(requestAndResult => {
          val (request, result) = requestAndResult
          assert(result.reply.isError)
          result.reply.getError.getMessage shouldBe s"Invalid Command ${request.getClass.getSimpleName} for State Empty"
          assert(result.hasNoEvents)
          result.state shouldBe a[InitialEmptyOrganizationState]
        })
      }


    }

    "in draft state" should {

      "Retrieve info on request" in new Established {
        val request: GetOrganizationInfoRequest = TestData.getOrganizationInfoRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)
        assert(result.state == establishedResult.state)
        private val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
        result.reply.getValue.asInstanceOf[OrganizationInfo] shouldBe OrganizationInfo(
          request.orgId,
          Some(Info(
            name = oldState.requiredDraftInfo.flatMap(_.name),
            shortName = oldState.optionalDraftInfo.flatMap(_.shortName),
            address = oldState.optionalDraftInfo.flatMap(_.address),
            isPrivate = oldState.requiredDraftInfo.map(_.isPrivate),
            url = oldState.optionalDraftInfo.flatMap(_.url),
            logo = oldState.optionalDraftInfo.flatMap(_.logo),
            tenant = oldState.requiredDraftInfo.flatMap(_.tenant)
          ))
        )
      }

      "Retrieve organization on request" in new Established {
        val request: GetOrganizationByIdRequest = TestData.getOrganizationByIdRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)
        assert(result.state == establishedResult.state)
        private val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
        result.reply.getValue.asInstanceOf[com.improving.app.organization.Organization] shouldBe com.improving.app.organization.Organization(
          orgId = request.orgId,
          info = Some(Info(
            name = oldState.requiredDraftInfo.flatMap(_.name),
            shortName = oldState.optionalDraftInfo.flatMap(_.shortName),
            address = oldState.optionalDraftInfo.flatMap(_.address),
            isPrivate = oldState.requiredDraftInfo.map(_.isPrivate),
            url = oldState.optionalDraftInfo.flatMap(_.url),
            logo = oldState.optionalDraftInfo.flatMap(_.logo),
            tenant = oldState.requiredDraftInfo.flatMap(_.tenant)
          )),
          parent = oldState.optionalDraftInfo.flatMap(_.parent),
          members = oldState.getMembers,
          owners = oldState.getOwners,
          contacts = oldState.getContacts,
          meta = oldState.orgMeta
        )
      }

      "Edit info when commanded" in new Established {
        val request: EditOrganizationInfoRequest = editOrganizationInfoRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationInfoEdited]
        result.event shouldBe OrganizationInfoEdited(
          orgId = Some(OrganizationId(orgId)),
          info = request.info,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo.get shouldBe establishedResult.state.asInstanceOf[DraftOrganizationState].requiredDraftInfo.get.copy(
              name = request.info.flatMap(_.name),
              isPrivate = request.info.flatMap(_.isPrivate).get
            )
            optionalDraftInfo.get shouldBe establishedResult.state.asInstanceOf[DraftOrganizationState].optionalDraftInfo.get.copy(
              shortName = establishOrganizationRequest.info.get.shortName,
              url = request.info.get.url,
              logo = request.info.get.logo
            )
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember

          case _ => fail("Incorrect state")
        }
      }

      "Succeed on edit info with empty info fields" in new Established {
        val request: EditOrganizationInfoRequest = editOrganizationInfoRequest.copy(info = Some(Info()))
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationInfoEdited]
        result.event shouldBe OrganizationInfoEdited(
          orgId = Some(OrganizationId(orgId)),
          info = request.info,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember

          case _ => fail("Incorrect state")
        }
      }

      "Update parent on command" in new Established {
        val request: UpdateParentRequest = TestData.updateParentRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[ParentUpdated]
        result.event shouldBe ParentUpdated(
            orgId = Some(OrganizationId(orgId)),
            newParent = request.newParent,
            actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo.map(_.copy(parent = request.newParent))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

      "Remove parent if newParent = None in UpdateParentRequest" in new Established {
        val request: UpdateParentRequest = TestData.updateParentRequest.copy(newParent = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[ParentUpdated]
        result.event shouldBe ParentUpdated(
          orgId = Some(OrganizationId(orgId)),
          newParent = None,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo.map(_.copy(parent = None))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

      "Add members on command (without duplicates)" in new Established {
        val request: AddMembersToOrganizationRequest = addMembersToOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersAddedToOrganization]
        result.event shouldBe MembersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newMembers = request.newMembers,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo.map(_.copy(members = testMembers ++ testNewMembers))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on AddMembers command with no new members" in new Established {
        val request: AddMembersToOrganizationRequest = addMembersToOrganizationRequest.copy(newMembers = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersAddedToOrganization]
        result.event shouldBe MembersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newMembers = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Remove members on command" in new Established {
        val request: RemoveMembersFromOrganizationRequest = removeMembersFromOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersRemovedFromOrganization]
        result.event shouldBe MembersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedMembers = request.removedMembers,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo.map(_.copy(members = testMembers.filterNot(request.removedMembers.contains)))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on remove members with no members to remove" in new Established {
        val request: RemoveMembersFromOrganizationRequest = removeMembersFromOrganizationRequest.copy(removedMembers = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersRemovedFromOrganization]
        result.event shouldBe MembersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedMembers = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Add owners on command (without duplicates)" in new Established {
        val request: AddOwnersToOrganizationRequest = addOwnersToOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersAddedToOrganization]
        result.event shouldBe OwnersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newOwners = request.newOwners,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo.map(_.copy(owners = testOwners ++ testNewOwners))
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on add owners with no new owners" in new Established {
        val request: AddOwnersToOrganizationRequest = addOwnersToOrganizationRequest.copy(newOwners = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersAddedToOrganization]
        result.event shouldBe OwnersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newOwners = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Remove owners on command" in new Established {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersRemovedFromOrganization]
        result.event shouldBe OwnersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedOwners = request.removedOwners,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo.map(_.copy(owners = testOwners.filterNot(request.removedOwners.contains)))
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

    "Succeed on remove owners with no owners to remove" in new Established {
      val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest.copy(removedOwners = Seq.empty[MemberId])
      private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
      assert(result.reply.isSuccess)
      result.reply.getValue shouldBe a[OrganizationEventResponse]
      result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersRemovedFromOrganization]
      result.event shouldBe OwnersRemovedFromOrganization(
        orgId = Some(OrganizationId(orgId)),
        removedOwners = Seq.empty[MemberId],
        actingMember = request.actingMember
      )
      result.state match {
        case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
          val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
          dOrgId shouldBe Some(OrganizationId(orgId))
          requiredDraftInfo shouldBe oldState.requiredDraftInfo.map(_.copy(owners = testOwners.filterNot(request.removedOwners.contains)))
          optionalDraftInfo shouldBe oldState.optionalDraftInfo
          orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
        case _ => fail("Incorrect state")
      }
    }

      "Update contacts on command" in new Established {
        val request: UpdateOrganizationContactsRequest = updateOrganizationContactsRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationContactsUpdated]
        result.event shouldBe OrganizationContactsUpdated(
          orgId = Some(OrganizationId(orgId)),
          contacts = request.contacts,
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo.map(_.copy(contacts = testContacts ++ testNewContacts))
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on update contacts with no new contacts" in new Established {
        val request: UpdateOrganizationContactsRequest = updateOrganizationContactsRequest.copy(contacts = Seq.empty[Contacts])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationContactsUpdated]
        result.event shouldBe OrganizationContactsUpdated(
          orgId = Some(OrganizationId(orgId)),
          contacts = Seq.empty[Contacts],
          actingMember = request.actingMember
        )
        result.state match {
          case DraftOrganizationState(dOrgId, requiredDraftInfo, optionalDraftInfo, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            dOrgId shouldBe Some(OrganizationId(orgId))
            requiredDraftInfo shouldBe oldState.requiredDraftInfo
            optionalDraftInfo shouldBe oldState.optionalDraftInfo
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_DRAFT) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Activate the organization when commanded" in new Established {
        val request: ActivateOrganizationRequest = activateOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationActivated]
        result.event shouldBe OrganizationActivated(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, orgMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            sOrgId shouldBe Some(OrganizationId(orgId))
            info shouldBe Some(Info(
                name = oldState.requiredDraftInfo.flatMap(_.name),
                shortName = oldState.optionalDraftInfo.flatMap(_.shortName),
                isPrivate = oldState.requiredDraftInfo.map(_.isPrivate),
                tenant = oldState.requiredDraftInfo.flatMap(_.tenant),
                address = oldState.optionalDraftInfo.flatMap(_.address),
                url = oldState.optionalDraftInfo.flatMap(_.url),
                logo = oldState.optionalDraftInfo.flatMap(_.logo)
            ))
            parent shouldBe oldState.optionalDraftInfo.flatMap(_.parent)
            members shouldBe oldState.optionalDraftInfo.map(_.members).get
            owners shouldBe oldState.requiredDraftInfo.map(_.owners).get
            contacts shouldBe oldState.optionalDraftInfo.map(_.contacts).get
            orgMeta.map(_.currentStatus).contains(ORGANIZATION_STATUS_ACTIVE) shouldBe true
            orgMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Terminate the organization when commanded" in new Established {
        val request: TerminateOrganizationRequest = terminateOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationTerminated]
        result.event shouldBe OrganizationTerminated(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case TerminatedOrganizationState(orgId, lastMeta, _) =>
            val oldState = establishedResult.state.asInstanceOf[DraftOrganizationState]
            orgId shouldBe oldState.orgId
            lastMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
            lastMeta.map(_.currentStatus) shouldBe Some(ORGANIZATION_STATUS_TERMINATED)
          case _ => fail("incorrect state")
        }
      }

      "Fail if the organization is already established" in new Established {
        val request: EstablishOrganizationRequest = establishOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid Command EstablishOrganizationRequest for State Draft"
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail if you attempt to remove all existing owners" in new Established {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest.copy(removedOwners = testOwners)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: the result of removing the requested owners cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on edit info command with with info = None" in new Established {
        val request: EditOrganizationInfoRequest = editOrganizationInfoRequest.copy(info = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: info cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on update parent with missing data" in new Established {
        val request: UpdateParentRequest = updateParentRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on add members with missing data" in new Established {
        val request: AddMembersToOrganizationRequest = addMembersToOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on removing members with missing data" in new Established {
        val request: RemoveMembersFromOrganizationRequest = removeMembersFromOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on adding owners with missing data" in new Established {
        val request: AddOwnersToOrganizationRequest = addOwnersToOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on removing owners with missing data" in new Established {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on update contacts with missing data" in new Established {
        val request: UpdateOrganizationContactsRequest = updateOrganizationContactsRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on activate command with missing data" in new Established {
        val request: ActivateOrganizationRequest = activateOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "Fail on terminate command with missing data" in new Established {
        val request: TerminateOrganizationRequest = terminateOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe establishedResult.state
      }

      "fail on all other requests" in new Established {
        everyMinimalRequest.filterNot(request =>
          request.isInstanceOf[EditOrganizationInfoRequest] ||
          request.isInstanceOf[UpdateParentRequest] ||
          request.isInstanceOf[AddMembersToOrganizationRequest] ||
          request.isInstanceOf[RemoveMembersFromOrganizationRequest] ||
          request.isInstanceOf[AddOwnersToOrganizationRequest] ||
          request.isInstanceOf[RemoveOwnersFromOrganizationRequest] ||
          request.isInstanceOf[UpdateOrganizationContactsRequest] ||
          request.isInstanceOf[GetOrganizationByIdRequest] ||
          request.isInstanceOf[GetOrganizationInfoRequest] ||
          request.isInstanceOf[TerminateOrganizationRequest] ||
          request.isInstanceOf[ActivateOrganizationRequest]
        ).map(
          request => (request, eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _)))
        ).foreach(requestAndResult => {
          val (request, result) = requestAndResult
          assert(result.reply.isError)
          result.reply.getError.getMessage shouldBe s"Invalid Command ${request.getClass.getSimpleName} for State Draft"
          assert(result.hasNoEvents)
          result.state shouldBe establishedResult.state
        })
      }

    }

    "In active state" should {

      "Retrieve info on request" in new Active {
        val request: GetOrganizationInfoRequest = TestData.getOrganizationInfoRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)
        assert(result.state == activatedResult.state)
        result.reply.getValue.asInstanceOf[OrganizationInfo] shouldBe OrganizationInfo(
          request.orgId,
          activatedResult.state.asInstanceOf[EstablishedOrganizationState].info,
        )
      }

      "Retrieve organization on request" in new Active  {
        val request: GetOrganizationByIdRequest = TestData.getOrganizationByIdRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)
        assert(result.state == activatedResult.state)
        result.reply.getValue.asInstanceOf[com.improving.app.organization.Organization] shouldBe com.improving.app.organization.Organization(
          orgId = request.orgId,
          info = oldState.info,
          parent = oldState.parent,
          members = oldState.getMembers,
          owners = oldState.getOwners,
          contacts = oldState.getContacts,
          meta = oldState.meta
        )
      }


      "Edit info when commanded" in new Active {
        val request: EditOrganizationInfoRequest = editOrganizationInfoRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationInfoEdited]
        result.event shouldBe OrganizationInfoEdited(
          orgId = Some(OrganizationId(orgId)),
          info = request.info,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            val oldState = activatedResult.state.asInstanceOf[EstablishedOrganizationState]
            val requestInfo = request.info.get
            val oldInfo = oldState.info.get
            sOrgId shouldBe Some(OrganizationId(orgId))
            info shouldBe Some(Info(
              name = requestInfo.name,
              shortName = oldInfo.shortName,
              address = oldInfo.address,
              isPrivate = requestInfo.isPrivate,
              url = requestInfo.url,
              logo = requestInfo.logo,
              tenant = oldInfo.tenant
            ))
            validateNewState(sOrgId, None, Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on edit info with empty info fields" in new Active {
        val request: EditOrganizationInfoRequest = editOrganizationInfoRequest.copy(info = Some(Info()))
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationInfoEdited]
        result.event shouldBe OrganizationInfoEdited(
          orgId = Some(OrganizationId(orgId)),
          info = request.info,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Update parent on command" in new Active {
        val request: UpdateParentRequest = TestData.updateParentRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[ParentUpdated]
        result.event shouldBe ParentUpdated(
          orgId = Some(OrganizationId(orgId)),
          newParent = request.newParent,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), None, Some(members), Some(owners), Some(contacts), meta, request.actingMember)
            parent shouldBe request.newParent
          case _ => fail("Incorrect state")
        }
      }

      "Remove parent if newParent = None in UpdateParentRequest" in new Active {
        val request: UpdateParentRequest = TestData.updateParentRequest.copy(newParent = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[ParentUpdated]
        result.event shouldBe ParentUpdated(
          orgId = Some(OrganizationId(orgId)),
          newParent = None,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), None, Some(members), Some(owners), Some(contacts), meta, request.actingMember)
            parent shouldBe None
          case _ => fail("Incorrect state")
        }
      }

      "Add members on command (without duplicates)" in new Active {
        val request: AddMembersToOrganizationRequest = addMembersToOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersAddedToOrganization]
        result.event shouldBe MembersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newMembers = request.newMembers,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), None, Some(owners), Some(contacts), meta, request.actingMember)
            members shouldBe testMembers ++ testNewMembers
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on AddMembers command with no new members" in new Active {
        val request: AddMembersToOrganizationRequest = addMembersToOrganizationRequest.copy(newMembers = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersAddedToOrganization]
        result.event shouldBe MembersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newMembers = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Remove members on command" in new Active {
        val request: RemoveMembersFromOrganizationRequest = removeMembersFromOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersRemovedFromOrganization]
        result.event shouldBe MembersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedMembers = request.removedMembers,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), None, Some(owners), Some(contacts), meta, request.actingMember)
            members shouldBe testMembers.filterNot(request.removedMembers.contains)
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on remove members with no members to remove" in new Active {
        val request: RemoveMembersFromOrganizationRequest = removeMembersFromOrganizationRequest.copy(removedMembers = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[MembersRemovedFromOrganization]
        result.event shouldBe MembersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedMembers = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Add owners on command (without duplicates)" in new Active {
        val request: AddOwnersToOrganizationRequest = addOwnersToOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersAddedToOrganization]
        result.event shouldBe OwnersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newOwners = request.newOwners,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), None, Some(contacts), meta, request.actingMember)
            owners shouldBe testOwners ++ testNewOwners
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on add owners with no new owners" in new Active {
        val request: AddOwnersToOrganizationRequest = addOwnersToOrganizationRequest.copy(newOwners = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersAddedToOrganization]
        result.event shouldBe OwnersAddedToOrganization(
          orgId = Some(OrganizationId(orgId)),
          newOwners = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Remove owners on command" in new Active {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersRemovedFromOrganization]
        result.event shouldBe OwnersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedOwners = request.removedOwners,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), None, Some(contacts), meta, request.actingMember)
            owners shouldBe testOwners.filterNot(request.removedOwners.contains)
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on remove owners with no owners to remove" in new Active {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest.copy(removedOwners = Seq.empty[MemberId])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OwnersRemovedFromOrganization]
        result.event shouldBe OwnersRemovedFromOrganization(
          orgId = Some(OrganizationId(orgId)),
          removedOwners = Seq.empty[MemberId],
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Update contacts on command" in new Active {
        val request: UpdateOrganizationContactsRequest = updateOrganizationContactsRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationContactsUpdated]
        result.event shouldBe OrganizationContactsUpdated(
          orgId = Some(OrganizationId(orgId)),
          contacts = request.contacts,
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), None, meta, request.actingMember)
            contacts shouldBe testContacts ++ testNewContacts
          case _ => fail("Incorrect state")
        }
      }

      "Succeed on update contacts with no new contacts" in new Active {
        val request: UpdateOrganizationContactsRequest = updateOrganizationContactsRequest.copy(contacts = Seq.empty[Contacts])
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationContactsUpdated]
        result.event shouldBe OrganizationContactsUpdated(
          orgId = Some(OrganizationId(orgId)),
          contacts = Seq.empty[Contacts],
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(sOrgId, info, parent, members, owners, contacts, meta, _) =>
            validateNewState(sOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), meta, request.actingMember)
          case _ => fail("Incorrect state")
        }
      }

      "Suspend upon command" in new Active {
        val request: SuspendOrganizationRequest = TestData.suspendOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationSuspended]
        result.event shouldBe OrganizationSuspended(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(dOrgId, info, parent, members, owners, contacts, orgMeta, _) =>
            validateNewState(dOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), orgMeta, request.actingMember, ORGANIZATION_STATUS_SUSPENDED)
          case _ => fail("Incorrect state")
        }
      }

      "Terminate upon request" in new Active {
        val request: TerminateOrganizationRequest = TestData.terminateOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationTerminated]
        result.event shouldBe OrganizationTerminated(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case TerminatedOrganizationState(tOrgId, lastMeta, _) =>
            tOrgId shouldBe Some(OrganizationId(orgId))
            lastMeta.map(_.currentStatus) shouldBe Some(ORGANIZATION_STATUS_TERMINATED)
            lastMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Fail if you attempt to remove all existing owners" in new Active {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest.copy(removedOwners = testOwners)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: the result of removing the requested owners cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on edit info command with with info = None" in new Active {
        val request: EditOrganizationInfoRequest = editOrganizationInfoRequest.copy(info = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: info cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on update parent with missing data" in new Active {
        val request: UpdateParentRequest = updateParentRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on add members with missing data" in new Active {
        val request: AddMembersToOrganizationRequest = addMembersToOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on removing members with missing data" in new Active {
        val request: RemoveMembersFromOrganizationRequest = removeMembersFromOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on adding owners with missing data" in new Active {
        val request: AddOwnersToOrganizationRequest = addOwnersToOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on removing owners with missing data" in new Active {
        val request: RemoveOwnersFromOrganizationRequest = removeOwnersFromOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on update contacts with missing data" in new Active {
        val request: UpdateOrganizationContactsRequest = updateOrganizationContactsRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "Fail on terminate command with missing data" in new Active {
        val request: TerminateOrganizationRequest = terminateOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe activatedResult.state
      }

      "fail on all other requests" in new Active {
        everyMinimalRequest.filterNot(request =>
          request.isInstanceOf[EditOrganizationInfoRequest] ||
            request.isInstanceOf[UpdateParentRequest] ||
            request.isInstanceOf[AddMembersToOrganizationRequest] ||
            request.isInstanceOf[RemoveMembersFromOrganizationRequest] ||
            request.isInstanceOf[AddOwnersToOrganizationRequest] ||
            request.isInstanceOf[RemoveOwnersFromOrganizationRequest] ||
            request.isInstanceOf[UpdateOrganizationContactsRequest] ||
            request.isInstanceOf[SuspendOrganizationRequest] ||
            request.isInstanceOf[GetOrganizationByIdRequest] ||
            request.isInstanceOf[GetOrganizationInfoRequest] ||
            request.isInstanceOf[TerminateOrganizationRequest]
        ).map(
          request => (request, eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _)))
        ).foreach(requestAndResult => {
          val (request, result) = requestAndResult
          assert(result.reply.isError)
          result.reply.getError.getMessage shouldBe s"Invalid Command ${request.getClass.getSimpleName} for State Active"
          assert(result.hasNoEvents)
          result.state shouldBe activatedResult.state
        })
      }


    }

    "In suspended state" should {

      "Retrieve info on request" in new Suspended {
        val request: GetOrganizationInfoRequest = TestData.getOrganizationInfoRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)
        assert(result.state == suspendedResult.state)
        result.reply.getValue.asInstanceOf[OrganizationInfo] shouldBe OrganizationInfo(
          request.orgId,
          suspendedResult.state.asInstanceOf[EstablishedOrganizationState].info,
        )
      }

      "Retrieve organization on request" in new Suspended {
        val request: GetOrganizationByIdRequest = TestData.getOrganizationByIdRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)
        assert(result.state == suspendedResult.state)
        result.reply.getValue.asInstanceOf[com.improving.app.organization.Organization] shouldBe com.improving.app.organization.Organization(
          orgId = request.orgId,
          info = oldState.info,
          parent = oldState.parent,
          members = oldState.getMembers,
          owners = oldState.getOwners,
          contacts = oldState.getContacts,
          meta = oldState.meta
        )
      }

      "Activate upon command" in new Suspended {
        val request: ActivateOrganizationRequest = TestData.activateOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationActivated]
        result.event shouldBe OrganizationActivated(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(dOrgId, info, parent, members, owners, contacts, orgMeta, _) =>
            validateNewState(dOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), orgMeta, request.actingMember, ORGANIZATION_STATUS_ACTIVE)
          case _ => fail("incorrect state")
        }
      }

      "Allow a second suspend command" in new Suspended {
        val request: SuspendOrganizationRequest = TestData.suspendOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationSuspended]
        result.event shouldBe OrganizationSuspended(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case EstablishedOrganizationState(dOrgId, info, parent, members, owners, contacts, orgMeta, _) =>
            validateNewState(dOrgId, Some(info), Some(parent), Some(members), Some(owners), Some(contacts), orgMeta, request.actingMember)
          case _ => fail("incorrect state")
        }
      }

      "Terminate upon request" in new Suspended {
        val request: TerminateOrganizationRequest = TestData.terminateOrganizationRequest
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isSuccess)
        result.reply.getValue shouldBe a[OrganizationEventResponse]
        result.reply.getValue.asInstanceOf[OrganizationEventResponse].organizationEvent shouldBe a[OrganizationTerminated]
        result.event shouldBe OrganizationTerminated(
          orgId = Some(OrganizationId(orgId)),
          actingMember = request.actingMember
        )
        result.state match {
          case TerminatedOrganizationState(tOrgId, lastMeta, _) =>
            tOrgId shouldBe Some(OrganizationId(orgId))
            lastMeta.map(_.currentStatus) shouldBe Some(ORGANIZATION_STATUS_TERMINATED)
            lastMeta.flatMap(_.lastUpdatedBy) shouldBe request.actingMember
          case _ => fail("Incorrect state")
        }
      }

      "Fail if activate is missing data" in new Suspended {
        val request: ActivateOrganizationRequest = activateOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe suspendedResult.state
      }

      "Fail if suspend is missing data" in new Suspended {
        val request: SuspendOrganizationRequest = suspendOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe suspendedResult.state
      }

      "Fail if terminate is missing data" in new Suspended {
        val request: TerminateOrganizationRequest = terminateOrganizationRequest.copy(actingMember = None)
        private val result = eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _))
        assert(result.reply.isError)
        result.reply.getError.getMessage shouldBe "Invalid request with errors: acting member cannot be empty."
        assert(result.hasNoEvents)
        result.state shouldBe suspendedResult.state
      }

      "Reject every other command" in new Suspended {
        everyMinimalRequest.filterNot(request =>
          request.isInstanceOf[ActivateOrganizationRequest] ||
          request.isInstanceOf[SuspendOrganizationRequest] ||
          request.isInstanceOf[TerminateOrganizationRequest] ||
          request.isInstanceOf[GetOrganizationByIdRequest] ||
          request.isInstanceOf[GetOrganizationInfoRequest]
        ).map(
          request => (request, eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _)))
        ).foreach(requestAndResult => {
          val (request, result) = requestAndResult
          assert(result.reply.isError)
          result.reply.getError.getMessage shouldBe s"Invalid Command ${request.getClass.getSimpleName} for State Suspended"
          assert(result.hasNoEvents)
          result.state shouldBe suspendedResult.state
        })
      }
    }

    "In terminated state" should {
      "Reject every command" in new Terminated {
        everyMinimalRequest.map(
          request => (request, eventSourcedTestKit.runCommand[StatusReply[OrganizationResponse]](OrganizationCommand(request, _)))
        ).foreach(requestAndResult => {
          val (request, result) = requestAndResult
          assert(result.reply.isError)
          result.reply.getError.getMessage shouldBe s"Invalid Command ${request.getClass.getSimpleName} for State Terminated"
          assert(result.hasNoEvents)
          result.state shouldBe terminatedResult.state
        })
      }
    }


  }




}

object OrganizationSpec {
  val config: Config = EventSourcedBehaviorTestKit.config
}
