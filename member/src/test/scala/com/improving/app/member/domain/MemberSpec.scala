package com.improving.app.member.domain

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import com.improving.app.common.domain.util.ContactUtil
import com.improving.app.common.domain.{MemberId, OrganizationId, TenantId}
import com.improving.app.member.domain.Member.{
  DefinedMemberState,
  DraftMemberState,
  MemberEnvelope,
  MemberState,
  RegisteredMemberState,
  TerminatedMemberState
}
import com.improving.app.member.domain.MemberState.{MEMBER_STATE_ACTIVE, MEMBER_STATE_DRAFT, MEMBER_STATE_SUSPENDED}
import com.improving.app.member.domain.TestData._
import com.improving.app.member.domain.util.MemberInfoUtil
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MemberSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with Matchers {
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[MemberEnvelope, MemberEvent, MemberState](
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
            MemberEnvelope(
              baseRegisterMember.copy(
                onBehalfOf = Some(MemberId("unauthorizedUser"))
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        // TODO: Determine how to process names with special characters
        // "error for invalid MemberInfo" in {
        //  val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
        //    MemberEnvelope(
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
        // }

        "succeed for golden path" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseRegisterMember,
              _
            )
          )

          val memberRegistered =
            result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.sealedValue.memberRegisteredValue.get

          memberRegistered.memberId shouldBe Some(MemberId(testMemberIdString))
          memberRegistered.memberInfo shouldBe Some(baseMemberInfo.toEditable)
          memberRegistered.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_DRAFT)
          memberRegistered.meta.flatMap(_.createdBy) shouldBe Some(MemberId("registeringMember"))

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo.getFirstName shouldBe "firstName"
          state.editableInfo.getContact shouldBe baseContact.toEditable
          state.editableInfo.getHandle shouldBe "handle"
          state.editableInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("registeringMember")

        }

        "error for registering the same member" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseRegisterMember,
              _
            )
          )
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
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
            MemberEnvelope(
              baseActivateMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on an uninitialized Member"
        }

        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseActivateMember.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseActivateMember,
              _
            )
          )

          val event = result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberActivatedValue
          event.memberId.map(_.id) shouldBe Some(testMemberIdString)
          event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_ACTIVE)
          event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("activatingMember")

          val state = result.stateOfType[DefinedMemberState]

          state.info.firstName shouldBe "firstName"
          state.info.contact shouldBe Some(baseContact)
          state.info.handle shouldBe "handle"
          state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("activatingMember")
        }
      }

      "executing SuspendMember" should {
        "error for being in the wrong state" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
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
            MemberEnvelope(
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
            MemberEnvelope(
              baseEditMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EditMemberInfo command cannot be used on an uninitialized Member"
        }

        "error for an unauthorized register user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseEditMemberInfo.copy(
                onBehalfOf = Some(MemberId("unauthorizedUser"))
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for valid empty editable info" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          val newInfo = baseEditableInfo.copy(
            organizationMembership = Seq.empty,
            firstName = None,
            notificationPreference = None
          )
          val resultInfo = baseEditableInfo.copy(
            organizationMembership = baseMemberInfo.organizationMembership,
            firstName = Some(baseMemberInfo.firstName),
            notificationPreference = None
          )
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseEditMemberInfo.copy(
                memberInfo = Some(newInfo)
              ),
              _
            )
          )

          val event = result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberInfoEdited
          event.memberId.map(_.id) shouldBe Some(testMemberIdString)
          event.newInfo shouldBe Some(resultInfo)
          event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_DRAFT)
          event.meta.flatMap(_.createdBy.map(_.id)) shouldBe Some("registeringMember")
          event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("editingMember")

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo.getFirstName shouldBe "firstName"
          state.editableInfo.getContact shouldBe editContact.toEditable
          state.editableInfo.getHandle shouldBe "editHandle"
          state.editableInfo.getAvatarUrl shouldBe "editAvatarUrl"
          state.editableInfo.getLastName shouldBe "editLastName"
          state.editableInfo.getTenant shouldBe TenantId("editTenantId")
          state.editableInfo.getNotificationPreference shouldBe NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
          state.editableInfo.organizationMembership shouldBe baseMemberInfo.organizationMembership
          state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("editingMember")
        }

        "succeed for completely filled editable info" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseEditMemberInfo,
              _
            )
          )

          val event = result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberInfoEdited
          event.memberId.map(_.id) shouldBe Some(testMemberIdString)
          event.newInfo shouldBe Some(
            baseEditableInfo
          )
          event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_DRAFT)
          event.meta.flatMap(_.createdBy.map(_.id)) shouldBe Some("registeringMember")
          event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("editingMember")

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo.getFirstName shouldBe "editFirstName"
          state.editableInfo.contact shouldBe Some(editContact.toEditable)
          state.editableInfo.getHandle shouldBe "editHandle"
          state.editableInfo.getAvatarUrl shouldBe "editAvatarUrl"
          state.editableInfo.getLastName shouldBe "editLastName"
          state.editableInfo.tenant shouldBe Some(TenantId("editTenantId"))
          state.editableInfo.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
          state.editableInfo.organizationMembership shouldBe Seq(OrganizationId("editOrg1"))
          state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("editingMember")
        }
      }

      "executing GetMemberData" should {
        "error when member has never been registered" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseGetMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldEqual "GetMemberInfo command cannot be used on an uninitialized Member"
        }

        "succeed and give the correct info after registering" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseGetMemberInfo,
              _
            )
          )

          val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

          getMemberInfo.memberId shouldBe Some(MemberId(testMemberIdString))
          getMemberInfo.memberInfo shouldBe Some(baseMemberInfo)
          getMemberInfo.memberMetaInfo.flatMap(_.createdBy.map(_.id)) shouldBe Some("registeringMember")
          getMemberInfo.memberMetaInfo.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("registeringMember")

          result.hasNoEvents

          val state = result.stateOfType[DraftMemberState]

          state.editableInfo shouldBe EditableInfo(
            contact = Some(baseContact.toEditable),
            handle = Some(baseMemberInfo.handle),
            avatarUrl = Some(baseMemberInfo.avatarUrl),
            firstName = Some(baseMemberInfo.firstName),
            lastName = Some(baseMemberInfo.lastName),
            tenant = baseMemberInfo.tenant,
            notificationPreference = baseMemberInfo.notificationPreference,
            organizationMembership = baseMemberInfo.organizationMembership
          )
          state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("registeringMember")
        }
      }
    }

    "in the RegisteredMemberState" when {
      "in the active handler" when {
        "executing RegisterMember" should {
          "error for already being registered" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseRegisterMember,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on an active Member"
          }
        }

        "executing ActivateMember" should {
          "error for already being activated" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseActivateMember.copy(
                  onBehalfOf = Some(MemberId("activatingMember2"))
                ),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on an active Member"
            result.hasNoEvents
            result.stateOfType[RegisteredMemberState].meta.lastModifiedBy.map(_.id) shouldBe Some("activatingMember")
          }
        }

        "executing SuspendMember" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseSuspendMember.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
          }

          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseSuspendMember,
                _
              )
            )

            val event =
              result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberSuspendedValue
            event.memberId.map(_.id) shouldBe Some(testMemberIdString)
            event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_SUSPENDED)
            event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("suspendingMember")

            val state = result.stateOfType[DefinedMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("suspendingMember")
            state.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing TerminateMember" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseTerminateMember.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
          }

          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseTerminateMember,
                _
              )
            )

            val event = result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberTerminated
            event.memberId.map(_.id) shouldBe Some(testMemberIdString)
            event.lastMeta.map(_.currentState) shouldBe Some(MEMBER_STATE_ACTIVE)
            event.lastMeta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("terminatingMember")

            val state = result.state.asInstanceOf[TerminatedMemberState]

            state.lastMeta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.lastMeta.lastModifiedBy.map(_.id) shouldBe Some("terminatingMember")
            state.lastMeta.currentState shouldBe MEMBER_STATE_ACTIVE
          }
        }

        "executing EditMemberInfo" should {
          "succeed for completely filled editable info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseEditMemberInfo,
                _
              )
            )

            val event = result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberInfoEdited
            event.memberId.map(_.id) shouldBe Some(testMemberIdString)
            event.newInfo shouldBe Some(baseEditableInfo)
            event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_ACTIVE)
            event.meta.flatMap(_.createdBy.map(_.id)) shouldBe Some("registeringMember")
            event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("editingMember")

            val state = result.stateOfType[DefinedMemberState]

            state.info.firstName shouldBe "editFirstName"
            state.info.contact shouldBe Some(editContact)
            state.info.handle shouldBe "editHandle"
            state.info.avatarUrl shouldBe "editAvatarUrl"
            state.info.lastName shouldBe "editLastName"
            state.info.tenant shouldBe Some(TenantId("editTenantId"))
            state.info.notificationPreference shouldBe Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
            state.info.organizationMembership shouldBe Seq(OrganizationId("editOrg1"))
            state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("editingMember")
            state.meta.currentState shouldBe MEMBER_STATE_ACTIVE
          }
        }

        "executing GetMemberData" should {
          "succeed and give the correct info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseGetMemberInfo,
                _
              )
            )

            val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

            getMemberInfo.memberId shouldBe Some(MemberId(testMemberIdString))
            getMemberInfo.memberInfo shouldBe Some(baseMemberInfo)
            getMemberInfo.memberMetaInfo.flatMap(_.createdBy.map(_.id)) shouldBe Some("registeringMember")
            getMemberInfo.memberMetaInfo.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("activatingMember")

            result.hasNoEvents

            val state = result.stateOfType[DefinedMemberState]

            state.info shouldBe baseMemberInfo
            state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("activatingMember")
          }
        }
      }
      "in the suspended handler" when {
        "executing RegisterMember" should {
          "error for already being registered" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseSuspendMember, _))
            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseRegisterMember,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on a suspended Member"
          }
        }

        "executing ActivateMember" should {
          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseActivateMember.copy(
                  onBehalfOf = Some(MemberId("activatingMember2"))
                ),
                _
              )
            )

            val event =
              result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberActivatedValue
            event.memberId.map(_.id) shouldBe Some(testMemberIdString)
            event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_ACTIVE)
            event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("activatingMember2")

            val state = result.stateOfType[DefinedMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("activatingMember2")
          }
        }

        "executing SuspendMember" should {
          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseSuspendMember,
                _
              )
            )

            val event =
              result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberSuspendedValue
            event.memberId.map(_.id) shouldBe Some(testMemberIdString)
            event.meta.map(_.currentState) shouldBe Some(MEMBER_STATE_SUSPENDED)
            event.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("suspendingMember")

            val state = result.stateOfType[DefinedMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe baseMemberInfo.organizationMembership
            state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("suspendingMember")
            state.meta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing TerminateMember" should {
          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseTerminateMember,
                _
              )
            )
            val event = result.reply.getValue.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberTerminated
            event.memberId.map(_.id) shouldBe Some(testMemberIdString)
            event.lastMeta.map(_.currentState) shouldBe Some(MEMBER_STATE_SUSPENDED)
            event.lastMeta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("terminatingMember")

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.lastMeta.lastModifiedBy.map(_.id) shouldBe Some("terminatingMember")
            state.lastMeta.currentState shouldBe MEMBER_STATE_SUSPENDED
          }
        }

        "executing EditMemberInfo" should {
          "error for not being able to process message" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseEditMemberInfo,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "EditMemberInfo command cannot be used on a suspended Member"
          }
        }

        "executing GetMemberData" should {
          "succeed and give the correct info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseSuspendMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberEnvelope(
                baseGetMemberInfo,
                _
              )
            )

            val getMemberInfo = result.reply.getValue.asMessage.sealedValue.memberStateValue.get

            getMemberInfo.memberId shouldBe Some(MemberId(testMemberIdString))
            getMemberInfo.memberInfo shouldBe Some(baseMemberInfo)
            getMemberInfo.memberMetaInfo.flatMap(_.createdBy.map(_.id)) shouldBe Some("registeringMember")
            getMemberInfo.memberMetaInfo.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("suspendingMember")

            result.hasNoEvents

            val state = result.stateOfType[DefinedMemberState]

            state.info shouldBe baseMemberInfo
            state.meta.createdBy.map(_.id) shouldBe Some("registeringMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("suspendingMember")
          }
        }
      }
    }

    "in the TerminatedMemberState" when {
      "executing RegisterMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseTerminateMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseRegisterMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "RegisterMember command cannot be used on a terminated Member"
        }
      }

      "executing ActivateMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseTerminateMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseActivateMember,
              _
            )
          )
          result.reply.getError.getMessage shouldBe "ActivateMember command cannot be used on a terminated Member"
        }
      }

      "executing SuspendMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseSuspendMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "SuspendMember command cannot be used on a terminated Member"
        }
      }

      "executing TerminateMember" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseTerminateMember,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "TerminateMember command cannot be used on a terminated Member"
        }
      }

      "executing EditMemberInfo" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
              baseEditMemberInfo,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EditMemberInfo command cannot be used on a terminated Member"
        }
      }

      "executing GetMemberData" should {
        "error for not being able to process message" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseRegisterMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseActivateMember, _))
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberEnvelope(baseTerminateMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberEnvelope(
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
