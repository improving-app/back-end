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

  "The member" when {
    "in the DraftMemberState" when {
      "executing RegisterMember" should {
        "error for an unauthorized registering user" ignore {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                registeringMember = MemberId("unauthorizedUser")
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        //TODO: Determine how to process names with special characters
        //"error for invalid MemberInfo" in {
        //  val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
        //    MemberCommand(
        //      baseRegisterMember.copy(
        //        memberInfo = baseMemberInfo.copy(firstName = "firstName1")
        //      ),
        //      _
        //    )
        //  )
//
        //  assert(
        //    result.reply.getError.getMessage
        //      .contains("First name cannot contain spaces, numbers or special characters.")
        //  )
        //}

        "succeed for golden path" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember,
              _
            )
          )

          val memberRegistered =
            result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberRegisteredValue.get

          memberRegistered.memberId shouldBe MemberId(testMemberIdString)
          memberRegistered.memberInfo shouldBe baseMemberInfo
          memberRegistered.meta.currentState shouldBe MEMBER_STATE_DRAFT
          memberRegistered.meta.createdBy shouldBe MemberId("registeringMember")

          val event = result.eventOfType[MemberRegistered]
          event.memberId.id shouldBe testMemberIdString
          event.memberInfo shouldBe baseMemberInfo
          event.meta.currentState shouldBe MEMBER_STATE_DRAFT
          event.meta.createdBy.id shouldBe "registeringMember"

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo.getFirstName shouldBe "firstName"
          state.editableInfo.getContact shouldBe baseContact
          state.editableInfo.getHandle shouldBe "handle"
          state.editableInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.id shouldBe "registeringMember"

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
        }

        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember.copy(activatingMember = MemberId("unauthorizedUser")),
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

          memberActivated.memberId shouldBe MemberId(testMemberIdString)
          memberActivated.meta.currentState shouldBe MEMBER_STATE_ACTIVE
          memberActivated.meta.lastModifiedBy.id shouldBe "activatingMember"

          val event = result.eventOfType[MemberActivated]
          event.memberId.id shouldBe testMemberIdString
          event.meta.currentState shouldBe MEMBER_STATE_ACTIVE
          event.meta.lastModifiedBy.id shouldBe "activatingMember"

          val state = result.stateOfType[RegisteredMemberState]

          state.info.firstName shouldBe "firstName"
          state.info.contact shouldBe baseContact
          state.info.handle shouldBe "handle"
          state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.id shouldBe "activatingMember"
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
        }

        "error for an unauthorized register user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                editingMember = MemberId("unauthorizedUser")
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for valid editable info" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = baseEditableInfo.copy(
                  organizationMembership = Seq.empty,
                  firstName = None,
                  notificationPreference = None
                )
              ),
              _
            )
          )

          val memberInfoEdited =
            result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberInfoEdited.get

          memberInfoEdited.memberId shouldBe MemberId(testMemberIdString)
          memberInfoEdited.memberInfo shouldBe baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            lastName = "editLastName",
            contact = editContact,
            tenant = TenantId("editTenantId")
          )
          memberInfoEdited.meta.currentState shouldBe MEMBER_STATE_DRAFT
          memberInfoEdited.meta.createdBy shouldBe MemberId("registeringMember")
          memberInfoEdited.meta.lastModifiedBy shouldBe MemberId("editingMember")

          val event = result.eventOfType[MemberInfoEdited]
          event.memberId.id shouldBe testMemberIdString
          event.memberInfo shouldBe baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            lastName = "editLastName",
            contact = editContact,
            tenant = TenantId("editTenantId")
          )
          event.meta.currentState shouldBe MEMBER_STATE_DRAFT
          event.meta.createdBy.id shouldBe "registeringMember"
          event.meta.lastModifiedBy.id shouldBe "editingMember"

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo.getFirstName shouldBe "firstName"
          state.editableInfo.getContact shouldBe editContact
          state.editableInfo.getHandle shouldBe "editHandle"
          state.editableInfo.getAvatarUrl shouldBe "editAvatarUrl"
          state.editableInfo.getLastName shouldBe "editLastName"
          state.editableInfo.getTenant shouldBe TenantId("editTenantId")
          state.editableInfo.getNotificationPreference shouldBe NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
          state.editableInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.id shouldBe "editingMember"
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

          memberInfoEdited.memberId shouldBe MemberId(testMemberIdString)
          memberInfoEdited.memberInfo shouldBe baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            firstName = "editFirstName",
            lastName = "editLastName",
            notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
            contact = editContact,
            tenant = TenantId("editTenantId"),
            organizationMembership = baseEditableInfo.organizationMembership
          )
          memberInfoEdited.meta.currentState shouldBe MEMBER_STATE_DRAFT
          memberInfoEdited.meta.createdBy shouldBe MemberId("registeringMember")
          memberInfoEdited.meta.lastModifiedBy shouldBe MemberId("editingMember")

          val event = result.eventOfType[MemberInfoEdited]
          event.memberId.id shouldBe testMemberIdString
          event.memberInfo shouldBe baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            firstName = "editFirstName",
            lastName = "editLastName",
            notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
            contact = editContact,
            tenant = TenantId("editTenantId"),
            organizationMembership = baseEditableInfo.organizationMembership
          )
          event.meta.currentState shouldBe MEMBER_STATE_DRAFT
          event.meta.createdBy.id shouldBe "registeringMember"
          event.meta.lastModifiedBy.id shouldBe "editingMember"

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo.getFirstName shouldBe "editFirstName"
          state.editableInfo.contact shouldBe Some(editContact)
          state.editableInfo.getHandle shouldBe "editHandle"
          state.editableInfo.getAvatarUrl shouldBe "editAvatarUrl"
          state.editableInfo.getLastName shouldBe "editLastName"
          state.editableInfo.tenant shouldBe Some(TenantId("editTenantId"))
          state.editableInfo.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
          state.editableInfo.organizationMembership shouldBe Seq(OrganizationId("editOrg1"))
          state.meta.createdBy.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.id shouldBe "editingMember"
        }
      }

      "executing GetMemberData" should {
        "error when member has never been registered" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseGetMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldEqual "GetMemberInfo command cannot be used on an uninitialized Member"
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

          getMemberInfo.memberId shouldBe MemberId(testMemberIdString)
          getMemberInfo.memberInfo shouldBe baseMemberInfo
          getMemberInfo.memberMetaInfo.createdBy.id shouldBe "registeringMember"
          getMemberInfo.memberMetaInfo.lastModifiedBy.id shouldBe "registeringMember"

          result.hasNoEvents

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo shouldBe EditableInfo(
            contact = Some(baseContact),
            handle = Some(baseMemberInfo.handle),
            avatarUrl = Some(baseMemberInfo.avatarUrl),
            firstName = Some(baseMemberInfo.firstName),
            lastName = Some(baseMemberInfo.lastName),
            tenant = Some(baseMemberInfo.tenant),
            notificationPreference = baseMemberInfo.notificationPreference,
            organizationMembership = baseMemberInfo.organizationMembership
          )
          state.meta.createdBy.id shouldBe "registeringMember"
          state.meta.lastModifiedBy.id shouldBe "registeringMember"
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
                  activatingMember = MemberId("activatingMember2")
                ),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on an active Member"
            result.hasNoEvents
            result.stateOfType[RegisteredMemberState].meta.lastModifiedBy.id shouldBe "activatingMember"
          }
        }

        "executing SuspendMember" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember.copy(suspendingMember = MemberId("unauthorizedUser")),
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

            memberSuspended.memberId shouldBe MemberId(testMemberIdString)
            memberSuspended.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
            memberSuspended.meta.lastModifiedBy.id shouldBe "suspendingMember"

            val event = result.eventOfType[MemberSuspended]
            event.memberId.id shouldBe testMemberIdString
            event.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
            event.meta.lastModifiedBy.id shouldBe "suspendingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe baseContact
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.id shouldBe "suspendingMember"
            state.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing TerminateMember" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember.copy(terminatingMember = MemberId("unauthorizedUser")),
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

            memberTerminated.memberId shouldBe MemberId(testMemberIdString)
            memberTerminated.lastMeta.currentState shouldBe MEMBER_STATE_ACTIVE
            memberTerminated.lastMeta.lastModifiedBy.id shouldBe "terminatingMember"

            val event = result.eventOfType[MemberTerminated]
            event.memberId.id shouldBe testMemberIdString
            event.lastMeta.currentState shouldBe MEMBER_STATE_ACTIVE
            event.lastMeta.lastModifiedBy.id shouldBe "terminatingMember"

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.id shouldBe "registeringMember"
            state.lastMeta.lastModifiedBy.id shouldBe "terminatingMember"
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

            memberInfoEdited.memberId shouldBe MemberId(testMemberIdString)
            memberInfoEdited.memberInfo shouldBe baseMemberInfo.copy(
              handle = "editHandle",
              avatarUrl = "editAvatarUrl",
              firstName = "editFirstName",
              lastName = "editLastName",
              notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
              contact = editContact,
              tenant = TenantId("editTenantId"),
              organizationMembership = baseEditableInfo.organizationMembership
            )
            memberInfoEdited.meta.currentState shouldBe MEMBER_STATE_ACTIVE
            memberInfoEdited.meta.createdBy shouldBe MemberId("registeringMember")
            memberInfoEdited.meta.lastModifiedBy shouldBe MemberId("editingMember")

            val event = result.eventOfType[MemberInfoEdited]
            event.memberId.id shouldBe testMemberIdString
            event.memberInfo shouldBe baseMemberInfo.copy(
              handle = "editHandle",
              avatarUrl = "editAvatarUrl",
              firstName = "editFirstName",
              lastName = "editLastName",
              notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
              contact = editContact,
              tenant = TenantId("editTenantId"),
              organizationMembership = baseEditableInfo.organizationMembership
            )
            event.meta.currentState shouldBe MEMBER_STATE_ACTIVE
            event.meta.createdBy.id shouldBe "registeringMember"
            event.meta.lastModifiedBy.id shouldBe "editingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "editFirstName"
            state.info.contact shouldBe editContact
            state.info.handle shouldBe "editHandle"
            state.info.avatarUrl shouldBe "editAvatarUrl"
            state.info.lastName shouldBe "editLastName"
            state.info.tenant shouldBe TenantId("editTenantId")
            state.info.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
            state.info.organizationMembership shouldBe Seq(OrganizationId("editOrg1"))
            state.meta.createdBy.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.id shouldBe "editingMember"
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

            getMemberInfo.memberId shouldBe MemberId(testMemberIdString)
            getMemberInfo.memberInfo shouldBe baseMemberInfo
            getMemberInfo.memberMetaInfo.createdBy.id shouldBe "registeringMember"
            getMemberInfo.memberMetaInfo.lastModifiedBy.id shouldBe "activatingMember"

            result.hasNoEvents

            val state = result.stateOfType[RegisteredMemberState]

            state.info shouldBe baseMemberInfo
            state.meta.createdBy.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.id shouldBe "activatingMember"
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
                  activatingMember = MemberId("activatingMember2")
                ),
                _
              )
            )

            val memberActivated =
              result.reply.getValue.asMessage.sealedValue.memberEventValue.get.memberEvent.asMessage.sealedValue.memberActivatedValue.get

            memberActivated.memberId shouldBe MemberId(testMemberIdString)
            memberActivated.meta.currentState shouldBe MEMBER_STATE_ACTIVE
            memberActivated.meta.lastModifiedBy.id shouldBe "activatingMember2"

            val event = result.eventOfType[MemberActivated]
            event.memberId.id shouldBe testMemberIdString
            event.meta.currentState shouldBe MEMBER_STATE_ACTIVE
            event.meta.lastModifiedBy.id shouldBe "activatingMember2"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe baseContact
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.id shouldBe "activatingMember2"
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

            memberSuspended.memberId shouldBe MemberId(testMemberIdString)
            memberSuspended.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
            memberSuspended.meta.lastModifiedBy.id shouldBe "suspendingMember"

            val event = result.eventOfType[MemberSuspended]
            event.memberId.id shouldBe testMemberIdString
            event.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
            event.meta.lastModifiedBy.id shouldBe "suspendingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe baseContact
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.id shouldBe "suspendingMember"
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

            memberTerminated.memberId shouldBe MemberId(testMemberIdString)
            memberTerminated.lastMeta.currentState shouldBe MEMBER_STATE_SUSPENDED
            memberTerminated.lastMeta.lastModifiedBy.id shouldBe "terminatingMember"

            val event = result.eventOfType[MemberTerminated]
            event.memberId.id shouldBe testMemberIdString
            event.lastMeta.currentState shouldBe MEMBER_STATE_SUSPENDED
            event.lastMeta.lastModifiedBy.id shouldBe "terminatingMember"

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.id shouldBe "registeringMember"
            state.lastMeta.lastModifiedBy.id shouldBe "terminatingMember"
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

            getMemberInfo.memberId shouldBe MemberId(testMemberIdString)
            getMemberInfo.memberInfo shouldBe baseMemberInfo
            getMemberInfo.memberMetaInfo.createdBy.id shouldBe "registeringMember"
            getMemberInfo.memberMetaInfo.lastModifiedBy.id shouldBe "suspendingMember"

            result.hasNoEvents

            val state = result.stateOfType[RegisteredMemberState]

            state.info shouldBe baseMemberInfo
            state.meta.createdBy.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.id shouldBe "suspendingMember"
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
