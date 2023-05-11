package com.improving.app.member.domain

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.member.domain.Member.{
  DraftMemberState,
  MemberCommand,
  MemberState,
  RegisteredMemberState,
  TerminatedMemberState,
  UninitializedMemberState
}
import com.improving.app.member.domain.MemberState.{MEMBER_STATE_ACTIVE, MEMBER_STATE_DRAFT, MEMBER_STATE_SUSPENDED}
import com.improving.app.member.domain.TestData._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MemberSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with Matchers {
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[MemberCommand, MemberEvent, MemberState](
    system,
    Member("testEntityTypeHint", testMemberIdString),
    SerializationSettings.disabled
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  private def validateFailedInitialRegisterMember(
      result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[
        MemberResponse
      ]]
  ): Unit = {
    result.hasNoEvents shouldBe true
    result.state shouldBe UninitializedMemberState()
  }

  private def validateFailedActivateMemberInDraftState(
      result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[
        MemberResponse
      ]]
  ): Unit = {
    result.hasNoEvents
    result.stateOfType[DraftMemberState].meta.currentState shouldBe MEMBER_STATE_DRAFT
    result.stateOfType[DraftMemberState].meta.lastModifiedBy.get.id shouldBe "registeringMember"
  }

  private def validateFailedSuspendMemberInActiveState(
      result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[
        MemberResponse
      ]]
  ): Unit = {
    result.hasNoEvents
    result.stateOfType[RegisteredMemberState].meta.currentState shouldBe MEMBER_STATE_ACTIVE
    result.stateOfType[RegisteredMemberState].meta.lastModifiedBy.get.id shouldBe "activatingMember"
  }

  private def validateFailedEditMemberInfoInDraftState(
      result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[
        MemberResponse
      ]]
  ): Unit = {
    result.hasNoEvents shouldBe true
    val state = result.stateOfType[DraftMemberState]
    state.requiredInfo.firstName shouldBe baseMemberInfo.firstName
    state.requiredInfo.contact shouldBe Some(baseContact)
    state.requiredInfo.handle shouldBe baseMemberInfo.handle
    state.requiredInfo.avatarUrl shouldBe baseMemberInfo.avatarUrl
    state.requiredInfo.lastName shouldBe baseMemberInfo.lastName
    state.requiredInfo.tenant shouldBe baseMemberInfo.tenant
    state.optionalInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
    state.optionalInfo.notificationPreference shouldBe baseMemberInfo.notificationPreference
    state.meta.createdBy.get.id shouldBe "registeringMember"
  }

  "The member" when {
    "in the DraftMemberState" when {
      "executing RegisterMember" should {
        "error for an unauthorized registering user" ignore {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                registeringMember = Some(MemberId("unauthorizedUser"))
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "error for invalid MemberInfo" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                memberInfo = Some(baseMemberInfo.copy(firstName = "firstName1"))
              ),
              _
            )
          )

          assert(
            result.reply.getError.getMessage
              .contains("First name cannot contain spaces, numbers or special characters.")
          )
          validateFailedInitialRegisterMember(result)
        }

        "succeed for golden path" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember,
              _
            )
          )

          val memberRegistered =
            result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberRegisteredValue.get

          memberRegistered.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberRegistered.memberInfo shouldBe Some(baseMemberInfo)
          memberRegistered.meta.get.currentState shouldBe MEMBER_STATE_DRAFT
          memberRegistered.meta.get.createdBy shouldBe Some(
            MemberId("registeringMember")
          )

          val event = result.eventOfType[MemberRegistered]
          event.memberId.get.id shouldBe testMemberIdString
          event.memberInfo.get shouldBe baseMemberInfo
          event.meta.get.currentState shouldBe MEMBER_STATE_DRAFT
          event.meta.get.createdBy.get.id shouldBe "registeringMember"

          val state = result.stateOfType[DraftMemberState]

          state.requiredInfo.firstName shouldBe "firstName"
          state.requiredInfo.contact shouldBe Some(baseContact)
          state.requiredInfo.handle shouldBe "handle"
          state.optionalInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.get.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.get.id shouldBe "registeringMember"

        }

        "error for registering the same member" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember,
              _
            )
          )
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on a draft Member"
        }
      }

      "executing ActivateMember" should {
        "error for a newly initialized member" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on an uninitialized Member"
          validateFailedInitialRegisterMember(result)
        }

        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember.copy(activatingMember = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember,
              _
            )
          )

          val memberActivated =
            result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberActivatedValue.get

          memberActivated.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberActivated.meta.get.currentState shouldBe MEMBER_STATE_ACTIVE
          memberActivated.meta.get.lastModifiedBy.get.id shouldBe "activatingMember"

          val event = result.eventOfType[MemberActivated]
          event.memberId.get.id shouldBe testMemberIdString
          event.meta.get.currentState shouldBe MEMBER_STATE_ACTIVE
          event.meta.get.lastModifiedBy.get.id shouldBe "activatingMember"

          val state = result.stateOfType[RegisteredMemberState]

          state.info.firstName shouldBe "firstName"
          state.info.contact shouldBe Some(baseContact)
          state.info.handle shouldBe "handle"
          state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.get.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.get.id shouldBe "activatingMember"
        }
      }

      "executing SuspendMember" should {
        "error for being in the wrong state" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseSuspendMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "SuspendMember command cannot be used on an uninitialized Member"
        }
      }

      "executing TerminateMember" should {
        "error for being in the wrong state" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseTerminateMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "TerminateMember command cannot be used on an uninitialized Member"
        }
      }

      "executing EditMemberInfo" should {
        "error for a newly initialized member" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EditMemberInfo command cannot be used on an uninitialized Member"
          validateFailedInitialRegisterMember(result)
        }

        "error for an unauthorized register user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                editingMember = Some(MemberId("unauthorizedUser"))
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
          validateFailedEditMemberInfoInDraftState(result)
        }

        "succeed for valid editable info" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = Some(
                  baseEditableInfo.copy(
                    organizationMembership = Seq.empty,
                    firstName = None,
                    notificationPreference = None
                  )
                )
              ),
              _
            )
          )

          val memberInfoEdited =
            result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberInfoEdited.get

          memberInfoEdited.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberInfoEdited.memberInfo shouldBe Some(
            baseMemberInfo.copy(
              handle = "editHandle",
              avatarUrl = "editAvatarUrl",
              lastName = "editLastName",
              contact = Some(editContact),
              tenant = Some(TenantId("editTenantId"))
            )
          )
          memberInfoEdited.meta.get.currentState shouldBe MEMBER_STATE_DRAFT
          memberInfoEdited.meta.get.createdBy shouldBe Some(
            MemberId("registeringMember")
          )
          memberInfoEdited.meta.get.lastModifiedBy shouldBe Some(
            MemberId("editingMember")
          )

          val event = result.eventOfType[MemberInfoEdited]
          event.memberId.get.id shouldBe testMemberIdString
          event.memberInfo.get shouldBe baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            lastName = "editLastName",
            contact = Some(editContact),
            tenant = Some(TenantId("editTenantId"))
          )
          event.meta.get.currentState shouldBe MEMBER_STATE_DRAFT
          event.meta.get.createdBy.get.id shouldBe "registeringMember"
          event.meta.get.lastModifiedBy.get.id shouldBe "editingMember"

          val state = result.stateOfType[DraftMemberState]

          state.requiredInfo.firstName shouldBe "firstName"
          state.requiredInfo.contact shouldBe Some(editContact)
          state.requiredInfo.handle shouldBe "editHandle"
          state.requiredInfo.avatarUrl shouldBe "editAvatarUrl"
          state.requiredInfo.lastName shouldBe "editLastName"
          state.requiredInfo.tenant shouldBe Some(TenantId("editTenantId"))
          state.optionalInfo.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL)
          state.optionalInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.get.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.get.id shouldBe "editingMember"
        }

        "succeed for completely filled editable info" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo,
              _
            )
          )

          val memberInfoEdited =
            result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberInfoEdited.get

          memberInfoEdited.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberInfoEdited.memberInfo shouldBe Some(
            baseMemberInfo.copy(
              handle = "editHandle",
              avatarUrl = "editAvatarUrl",
              firstName = "editFirstName",
              lastName = "editLastName",
              notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
              contact = Some(editContact),
              tenant = Some(TenantId("editTenantId")),
              organizationMembership = baseEditableInfo.organizationMembership
            )
          )
          memberInfoEdited.meta.get.currentState shouldBe MEMBER_STATE_DRAFT
          memberInfoEdited.meta.get.createdBy shouldBe Some(
            MemberId("registeringMember")
          )
          memberInfoEdited.meta.get.lastModifiedBy shouldBe Some(
            MemberId("editingMember")
          )

          val event = result.eventOfType[MemberInfoEdited]
          event.memberId.get.id shouldBe testMemberIdString
          event.memberInfo.get shouldBe baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            firstName = "editFirstName",
            lastName = "editLastName",
            notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
            contact = Some(editContact),
            tenant = Some(TenantId("editTenantId")),
            organizationMembership = baseEditableInfo.organizationMembership
          )
          event.meta.get.currentState shouldBe MEMBER_STATE_DRAFT
          event.meta.get.createdBy.get.id shouldBe "registeringMember"
          event.meta.get.lastModifiedBy.get.id shouldBe "editingMember"

          val state = result.stateOfType[DraftMemberState]

          state.requiredInfo.firstName shouldBe "editFirstName"
          state.requiredInfo.contact shouldBe Some(editContact)
          state.requiredInfo.handle shouldBe "editHandle"
          state.requiredInfo.avatarUrl shouldBe "editAvatarUrl"
          state.requiredInfo.lastName shouldBe "editLastName"
          state.requiredInfo.tenant shouldBe Some(TenantId("editTenantId"))
          state.optionalInfo.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
          state.optionalInfo.organizationMembership shouldBe Seq(OrganizationId("editOrg1"))
          state.meta.createdBy.get.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.get.id shouldBe "editingMember"
        }
      }

      "executing GetMemberData" should {
        "succeed and give the correct info before registering" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseGetMemberInfo,
              _
            )
          )

          val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

          getMemberInfo.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          getMemberInfo.memberInfo shouldBe None
          getMemberInfo.memberMetaInfo shouldBe None

          result.hasNoEvents

          result.state shouldBe UninitializedMemberState()
        }

        "succeed and give the correct info after registering" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseGetMemberInfo,
              _
            )
          )

          val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

          getMemberInfo.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          getMemberInfo.memberInfo shouldBe Some(baseMemberInfo)
          getMemberInfo.memberMetaInfo.get.createdBy.get.id shouldBe "registeringMember"
          getMemberInfo.memberMetaInfo.get.lastModifiedBy.get.id shouldBe "registeringMember"

          result.hasNoEvents

          val state = result.stateOfType[DraftMemberState]

          state.requiredInfo shouldBe RequiredDraftInfo(
            contact = Some(baseContact),
            handle = baseMemberInfo.handle,
            avatarUrl = baseMemberInfo.avatarUrl,
            firstName = baseMemberInfo.firstName,
            lastName = baseMemberInfo.lastName,
            tenant = baseMemberInfo.tenant
          )
          state.optionalInfo shouldBe OptionalDraftInfo(
            notificationPreference = baseMemberInfo.notificationPreference,
            organizationMembership = baseMemberInfo.organizationMembership
          )
          state.meta.createdBy.get.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.get.id shouldBe "registeringMember"
        }
      }
    }

    "in the RegisteredMemberState" when {
      "in the active handler" when {
        "executing RegisterMember" should {
          "error for already being registered" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseRegisterMember,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on an active Member"
          }
        }

        "executing ActivateMember" should {
          "error for already being activated" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseActivateMember.copy(
                  activatingMember = Some(MemberId("activatingMember2"))
                ),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on an active Member"
            result.hasNoEvents
            result.stateOfType[RegisteredMemberState].meta.lastModifiedBy.get.id shouldBe "activatingMember"
          }
        }

        "executing SuspendMember" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember.copy(suspendingMember = Some(MemberId("unauthorizedUser"))),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
          }

          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember,
                _
              )
            )

            val memberSuspended =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberSuspendedValue.get

            memberSuspended.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberSuspended.meta.get.currentState shouldBe MEMBER_STATE_SUSPENDED
            memberSuspended.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val event = result.eventOfType[MemberSuspended]
            event.memberId.get.id shouldBe testMemberIdString
            event.meta.get.currentState shouldBe MEMBER_STATE_SUSPENDED
            event.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "suspendingMember"
            state.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing TerminateMember" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember.copy(terminatingMember = Some(MemberId("unauthorizedUser"))),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
          }

          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember,
                _
              )
            )

            val memberTerminated =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberTerminated.get

            memberTerminated.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberTerminated.lastMeta.get.currentState shouldBe MEMBER_STATE_ACTIVE
            memberTerminated.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val event = result.eventOfType[MemberTerminated]
            event.memberId.get.id shouldBe testMemberIdString
            event.lastMeta.get.currentState shouldBe MEMBER_STATE_ACTIVE
            event.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.get.id shouldBe "registeringMember"
            state.lastMeta.lastModifiedBy.get.id shouldBe "terminatingMember"
            state.lastMeta.currentState shouldBe MEMBER_STATE_ACTIVE
          }
        }

        "executing EditMemberInfo" should {
          "succeed for completely filled editable info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseEditMemberInfo,
                _
              )
            )

            val memberInfoEdited =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberInfoEdited.get

            memberInfoEdited.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberInfoEdited.memberInfo shouldBe Some(
              baseMemberInfo.copy(
                handle = "editHandle",
                avatarUrl = "editAvatarUrl",
                firstName = "editFirstName",
                lastName = "editLastName",
                notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
                contact = Some(editContact),
                tenant = Some(TenantId("editTenantId")),
                organizationMembership = baseEditableInfo.organizationMembership
              )
            )
            memberInfoEdited.meta.get.currentState shouldBe MEMBER_STATE_ACTIVE
            memberInfoEdited.meta.get.createdBy shouldBe Some(
              MemberId("registeringMember")
            )
            memberInfoEdited.meta.get.lastModifiedBy shouldBe Some(
              MemberId("editingMember")
            )

            val event = result.eventOfType[MemberInfoEdited]
            event.memberId.get.id shouldBe testMemberIdString
            event.memberInfo.get shouldBe baseMemberInfo.copy(
              handle = "editHandle",
              avatarUrl = "editAvatarUrl",
              firstName = "editFirstName",
              lastName = "editLastName",
              notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
              contact = Some(editContact),
              tenant = Some(TenantId("editTenantId")),
              organizationMembership = baseEditableInfo.organizationMembership
            )
            event.meta.get.currentState shouldBe MEMBER_STATE_ACTIVE
            event.meta.get.createdBy.get.id shouldBe "registeringMember"
            event.meta.get.lastModifiedBy.get.id shouldBe "editingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "editFirstName"
            state.info.contact shouldBe Some(editContact)
            state.info.handle shouldBe "editHandle"
            state.info.avatarUrl shouldBe "editAvatarUrl"
            state.info.lastName shouldBe "editLastName"
            state.info.tenant shouldBe Some(TenantId("editTenantId"))
            state.info.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
            state.info.organizationMembership shouldBe Seq(OrganizationId("editOrg1"))
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "editingMember"
            state.meta.currentState shouldBe MEMBER_STATE_ACTIVE
          }
        }

        "executing GetMemberData" should {
          "succeed and give the correct info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseGetMemberInfo,
                _
              )
            )

            val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

            getMemberInfo.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            getMemberInfo.memberInfo shouldBe Some(baseMemberInfo)
            getMemberInfo.memberMetaInfo.get.createdBy.get.id shouldBe "registeringMember"
            getMemberInfo.memberMetaInfo.get.lastModifiedBy.get.id shouldBe "activatingMember"

            result.hasNoEvents

            val state = result.stateOfType[RegisteredMemberState]

            state.info shouldBe baseMemberInfo
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "activatingMember"
          }
        }
      }
      "in the suspended handler" when {
        "executing RegisterMember" should {
          "error for already being registered" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseSuspendMember, _))
            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseRegisterMember,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on a suspended Member"
          }
        }

        "executing ActivateMember" should {
          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseActivateMember.copy(
                  activatingMember = Some(MemberId("activatingMember2"))
                ),
                _
              )
            )

            val memberActivated =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberActivatedValue.get

            memberActivated.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberActivated.meta.get.currentState shouldBe MEMBER_STATE_ACTIVE
            memberActivated.meta.get.lastModifiedBy.get.id shouldBe "activatingMember2"

            val event = result.eventOfType[MemberActivated]
            event.memberId.get.id shouldBe testMemberIdString
            event.meta.get.currentState shouldBe MEMBER_STATE_ACTIVE
            event.meta.get.lastModifiedBy.get.id shouldBe "activatingMember2"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "activatingMember2"
          }
        }

        "executing SuspendMember" should {
          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember,
                _
              )
            )

            val memberSuspended =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberSuspendedValue.get

            memberSuspended.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberSuspended.meta.get.currentState shouldBe MEMBER_STATE_SUSPENDED
            memberSuspended.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val event = result.eventOfType[MemberSuspended]
            event.memberId.get.id shouldBe testMemberIdString
            event.meta.get.currentState shouldBe MEMBER_STATE_SUSPENDED
            event.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "suspendingMember"
            state.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing TerminateMember" should {
          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember,
                _
              )
            )

            val memberTerminated =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberTerminated.get

            memberTerminated.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberTerminated.lastMeta.get.currentState shouldBe MEMBER_STATE_SUSPENDED
            memberTerminated.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val event = result.eventOfType[MemberTerminated]
            event.memberId.get.id shouldBe testMemberIdString
            event.lastMeta.get.currentState shouldBe MEMBER_STATE_SUSPENDED
            event.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.get.id shouldBe "registeringMember"
            state.lastMeta.lastModifiedBy.get.id shouldBe "terminatingMember"
            state.lastMeta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing EditMemberInfo" should {
          "error for not being able to process message" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseEditMemberInfo,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "EditMemberInfo command cannot be used on a suspended Member"
          }
        }

        "executing GetMemberData" should {
          "succeed and give the correct info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseGetMemberInfo,
                _
              )
            )

            val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

            getMemberInfo.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            getMemberInfo.memberInfo shouldBe Some(baseMemberInfo)
            getMemberInfo.memberMetaInfo.get.createdBy.get.id shouldBe "registeringMember"
            getMemberInfo.memberMetaInfo.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            result.hasNoEvents

            val state = result.stateOfType[RegisteredMemberState]

            state.info shouldBe baseMemberInfo
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "suspendingMember"
          }
        }
      }
    }

    "in the TerminatedMemberState" when {
      "executing RegisterMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseTerminateMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on a terminated Member"
        }
      }

      "executing ActivateMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseTerminateMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on a terminated Member"
        }
      }

      "executing SuspendMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseSuspendMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "SuspendMember command cannot be used on a terminated Member"
        }
      }

      "executing TerminateMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseTerminateMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "TerminateMember command cannot be used on a terminated Member"
        }
      }

      "executing EditMemberInfo" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EditMemberInfo command cannot be used on a terminated Member"
        }
      }

      "executing GetMemberData" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseGetMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "GetMemberInfo command cannot be used on a terminated Member"
        }
      }
    }
  }

}
