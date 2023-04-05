package com.improving.app.member.domain

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.member.domain.Member.{DraftMemberState, MemberCommand, MemberState, RegisteredMemberState, TerminatedMemberState}
import com.improving.app.member.domain.MemberStatus.{MEMBER_STATUS_ACTIVE, MEMBER_STATUS_DRAFT, MEMBER_STATUS_SUSPENDED}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import com.improving.app.member.domain.TestData._

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
   result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[MemberResponse]]
  ): Unit = {
    result.hasNoEvents shouldBe true
    val state = result.stateOfType[DraftMemberState]
    state.requiredInfo.firstName shouldBe ""
    state.requiredInfo.contact shouldBe None
    state.requiredInfo.handle shouldBe ""
    state.optionalInfo.organizationMembership shouldBe Seq.empty
    state.meta.createdBy shouldBe None
  }

  private def validateFailedActivateMemberInDraftState(
    result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[MemberResponse]]
  ): Unit = {
    result.hasNoEvents
    result.stateOfType[DraftMemberState].meta.memberStatus shouldBe MEMBER_STATUS_DRAFT
    result.stateOfType[DraftMemberState].meta.lastModifiedBy.get.id shouldBe "registeringMember"
  }

  private def validateFailedSuspendMemberInActiveState(
    result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[MemberResponse]]
  ): Unit = {
    result.hasNoEvents
    result.stateOfType[RegisteredMemberState].meta.memberStatus shouldBe MEMBER_STATUS_ACTIVE
    result.stateOfType[RegisteredMemberState].meta.lastModifiedBy.get.id shouldBe "activatingMember"
  }

  private def validateFailedEditMemberInfoInDraftState(
    result: EventSourcedBehaviorTestKit.CommandResultWithReply[MemberCommand, MemberEvent, MemberState, StatusReply[MemberResponse]]
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

        "error for empty memberId" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                memberId = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member ID associated"))
          validateFailedInitialRegisterMember(result)
        }

        "error for memberId with empty string" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                memberId = Some(MemberId())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Member ID is empty"))
          validateFailedInitialRegisterMember(result)
        }

        "error for empty MemberInfo" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                memberInfo = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member info associated"))
          validateFailedInitialRegisterMember(result)
        }

        "error for incomplete MemberInfo" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                memberInfo = Some(MemberInfo())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("First name is empty"))
          assert(result.reply.getError.getMessage.contains("Last name is empty"))
          assert(result.reply.getError.getMessage.contains("No contact information associated"))
          assert(result.reply.getError.getMessage.contains("No or Invalid tenant associated"))
          validateFailedInitialRegisterMember(result)
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

          assert(result.reply.getError.getMessage.contains("First name cannot contain spaces, numbers or special characters."))
          validateFailedInitialRegisterMember(result)
        }

        "error for empty registeringMember" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                registeringMember = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member ID associated"))
          validateFailedInitialRegisterMember(result)
        }

        "error for registeringMember with empty string" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember.copy(
                registeringMember = Some(MemberId())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Member ID is empty"))
          validateFailedInitialRegisterMember(result)
        }

        "succeed for golden path" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseRegisterMember,
              _
            )
          )

          val memberRegistered = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
            .memberEvent.asMessage.sealedValue.memberRegisteredValue.get

          memberRegistered.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberRegistered.memberInfo shouldBe Some(baseMemberInfo)
          memberRegistered.meta.get.memberStatus shouldBe MEMBER_STATUS_DRAFT
          memberRegistered.meta.get.createdBy shouldBe Some(
            MemberId("registeringMember")
          )

          val event = result.eventOfType[MemberRegistered]
          event.memberId.get.id shouldBe testMemberIdString
          event.memberInfo.get shouldBe baseMemberInfo
          event.meta.get.memberStatus shouldBe MEMBER_STATUS_DRAFT
          event.meta.get.createdBy.get.id shouldBe "registeringMember"

          val state = result.stateOfType[DraftMemberState]

          state.requiredInfo.firstName shouldBe "firstName"
          state.requiredInfo.contact shouldBe Some(baseContact)
          state.requiredInfo.handle shouldBe "handle"
          state.optionalInfo.organizationMembership shouldBe Seq(OrganizationId("org1"))
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

          result.reply.getError.getMessage shouldBe "Member has already been registered."
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

          result.reply.getError.getMessage shouldBe "A member not registered cannot be activated"
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

        "error for empty memberId" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember.copy(
                memberId = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member ID associated"))
          validateFailedActivateMemberInDraftState(result)
        }

        "error for memberId with empty string" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember.copy(
                memberId = Some(MemberId())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Member ID is empty"))
          validateFailedActivateMemberInDraftState(result)
        }

        "error for empty activatingMember" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember.copy(
                activatingMember = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member ID associated"))
          validateFailedActivateMemberInDraftState(result)
        }

        "error for activatingMember with empty string" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember.copy(
                activatingMember = Some(MemberId())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Member ID is empty"))
          validateFailedActivateMemberInDraftState(result)
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseActivateMember,
              _
            )
          )

          val memberActivated = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
            .memberEvent.asMessage.sealedValue.memberActivatedValue.get

          memberActivated.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberActivated.meta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
          memberActivated.meta.get.lastModifiedBy.get.id shouldBe "activatingMember"

          val event = result.eventOfType[MemberActivated]
          event.memberId.get.id shouldBe testMemberIdString
          event.meta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
          event.meta.get.lastModifiedBy.get.id shouldBe "activatingMember"

          val state = result.stateOfType[RegisteredMemberState]

          state.info.firstName shouldBe "firstName"
          state.info.contact shouldBe Some(baseContact)
          state.info.handle shouldBe "handle"
          state.info.organizationMembership shouldBe Seq(OrganizationId("org1"))
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

          result.reply.getError.getMessage shouldBe "Member has not yet been registered."
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

          result.reply.getError.getMessage shouldBe "Member has not yet been registered."
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

          result.reply.getError.getMessage shouldBe "A member not registered cannot be edited"
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

        "error for empty memberId" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberId = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member ID associated"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for memberId with empty string" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberId = Some(MemberId())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Member ID is empty"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for empty editingMember" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                editingMember = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No member ID associated"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for editingMember with empty string" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                editingMember = Some(MemberId())
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Member ID is empty"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for empty memberInfo" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = None
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("No editable info associated"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for filled Contact to not have complete information" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = Some(baseEditableInfo.copy(
                  contact = Some(Contact(
                    emailAddress = Some(""),
                    phone = Some("")
                  ))
                ))
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("First name is empty"))
          assert(result.reply.getError.getMessage.contains("Last name is empty"))
          assert(result.reply.getError.getMessage.contains("Missing or invalid phone number"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for filled organizations to have empty string" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = Some(baseEditableInfo.copy(
                  organizationMembership = Seq(OrganizationId())
                ))
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Empty organization id found"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "error for filled tenant to have empty string" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = Some(baseEditableInfo.copy(
                  tenant = Some(TenantId())
                ))
              ),
              _
            )
          )

          assert(result.reply.getError.getMessage.contains("Tenant Id is empty"))
          validateFailedEditMemberInfoInDraftState(result)
        }

        "succeed for valid editable info" in {
          eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
          val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
            MemberCommand(
              baseEditMemberInfo.copy(
                memberInfo = Some(baseEditableInfo.copy(
                  organizationMembership = Seq.empty,
                  firstName = None,
                  notificationPreference = None
                ))
              ),
              _
            )
          )

          val memberInfoEdited = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
            .memberEvent.asMessage.sealedValue.memberInfoEdited.get

          memberInfoEdited.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberInfoEdited.memberInfo shouldBe Some(baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            lastName = "editLastName",
            contact = Some(editContact),
            tenant = Some(TenantId("editTenantId"))
          ))
          memberInfoEdited.meta.get.memberStatus shouldBe MEMBER_STATUS_DRAFT
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
          event.meta.get.memberStatus shouldBe MEMBER_STATUS_DRAFT
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
          state.optionalInfo.organizationMembership shouldBe Seq(OrganizationId("org1"))
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

          val memberInfoEdited = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
            .memberEvent.asMessage.sealedValue.memberInfoEdited.get

          memberInfoEdited.memberId shouldBe Some(
            MemberId(testMemberIdString)
          )
          memberInfoEdited.memberInfo shouldBe Some(baseMemberInfo.copy(
            handle = "editHandle",
            avatarUrl = "editAvatarUrl",
            firstName = "editFirstName",
            lastName = "editLastName",
            notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
            contact = Some(editContact),
            tenant = Some(TenantId("editTenantId")),
            organizationMembership = baseEditableInfo.organizationMembership
          ))
          memberInfoEdited.meta.get.memberStatus shouldBe MEMBER_STATUS_DRAFT
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
          event.meta.get.memberStatus shouldBe MEMBER_STATUS_DRAFT
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

          val state = result.stateOfType[DraftMemberState]

          state.requiredInfo shouldBe RequiredDraftInfo()
          state.optionalInfo shouldBe OptionalDraftInfo()
          state.meta shouldBe MemberMetaInfo()
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

            result.reply.getError.getMessage shouldBe "Member has already been registered."
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

            result.reply.getError.getMessage shouldBe "Member has already been activated"
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

          "error for empty memberId" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember.copy(memberId = None),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("No member ID associated"))
            validateFailedSuspendMemberInActiveState(result)
          }

          "error for memberId with empty string" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember.copy(memberId = Some(MemberId())),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("Member ID is empty"))
            validateFailedSuspendMemberInActiveState(result)
          }

          "error for empty suspendingMember" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember.copy(suspendingMember = None),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("No member ID associated"))
            validateFailedSuspendMemberInActiveState(result)
          }

          "error for activating Member with empty string" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseSuspendMember.copy(suspendingMember = Some(MemberId())),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("Member ID is empty"))
            validateFailedSuspendMemberInActiveState(result)
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

            val memberSuspended = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
              .memberEvent.asMessage.sealedValue.memberSuspendedValue.get

            memberSuspended.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberSuspended.meta.get.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
            memberSuspended.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val event = result.eventOfType[MemberSuspended]
            event.memberId.get.id shouldBe testMemberIdString
            event.meta.get.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
            event.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe Seq(OrganizationId("org1"))
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "suspendingMember"
            state.meta.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
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

          "error for empty memberId" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember.copy(memberId = None),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("No member ID associated"))
            validateFailedSuspendMemberInActiveState(result)
          }

          "error for memberId with empty string" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember.copy(memberId = Some(MemberId())),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("Member ID is empty"))
            validateFailedSuspendMemberInActiveState(result)
          }

          "error for empty suspendingMember" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember.copy(terminatingMember = None),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("No member ID associated"))
            validateFailedSuspendMemberInActiveState(result)
          }

          "error for activating Member with empty string" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseTerminateMember.copy(terminatingMember = Some(MemberId())),
                _
              )
            )

            assert(result.reply.getError.getMessage.contains("Member ID is empty"))
            validateFailedSuspendMemberInActiveState(result)
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

            val memberTerminated = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
              .memberEvent.asMessage.sealedValue.memberTerminated.get

            memberTerminated.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberTerminated.lastMeta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
            memberTerminated.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val event = result.eventOfType[MemberTerminated]
            event.memberId.get.id shouldBe testMemberIdString
            event.lastMeta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
            event.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.get.id shouldBe "registeringMember"
            state.lastMeta.lastModifiedBy.get.id shouldBe "terminatingMember"
            state.lastMeta.memberStatus shouldBe MEMBER_STATUS_ACTIVE
          }
        }

        "executing EditMemberInfo" should  {
          "succeed for completely filled editable info" in {
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseRegisterMember, _))
            eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](MemberCommand(baseActivateMember, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[MemberResponse]](
              MemberCommand(
                baseEditMemberInfo,
                _
              )
            )

            val memberInfoEdited = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
              .memberEvent.asMessage.sealedValue.memberInfoEdited.get

            memberInfoEdited.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberInfoEdited.memberInfo shouldBe Some(baseMemberInfo.copy(
              handle = "editHandle",
              avatarUrl = "editAvatarUrl",
              firstName = "editFirstName",
              lastName = "editLastName",
              notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
              contact = Some(editContact),
              tenant = Some(TenantId("editTenantId")),
              organizationMembership = baseEditableInfo.organizationMembership
            ))
            memberInfoEdited.meta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
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
            event.meta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
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
            state.meta.memberStatus shouldBe MEMBER_STATUS_ACTIVE
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

            result.reply.getError.getMessage shouldBe "Member has already been registered."
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

            val memberActivated = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
              .memberEvent.asMessage.sealedValue.memberActivatedValue.get

            memberActivated.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberActivated.meta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
            memberActivated.meta.get.lastModifiedBy.get.id shouldBe "activatingMember2"

            val event = result.eventOfType[MemberActivated]
            event.memberId.get.id shouldBe testMemberIdString
            event.meta.get.memberStatus shouldBe MEMBER_STATUS_ACTIVE
            event.meta.get.lastModifiedBy.get.id shouldBe "activatingMember2"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe Seq(OrganizationId("org1"))
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

            val memberSuspended = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
              .memberEvent.asMessage.sealedValue.memberSuspendedValue.get

            memberSuspended.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberSuspended.meta.get.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
            memberSuspended.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val event = result.eventOfType[MemberSuspended]
            event.memberId.get.id shouldBe testMemberIdString
            event.meta.get.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
            event.meta.get.lastModifiedBy.get.id shouldBe "suspendingMember"

            val state = result.stateOfType[RegisteredMemberState]

            state.info.firstName shouldBe "firstName"
            state.info.contact shouldBe Some(baseContact)
            state.info.handle shouldBe "handle"
            state.info.organizationMembership shouldBe Seq(OrganizationId("org1"))
            state.meta.createdBy.get.id shouldBe "registeringMember"
            state.meta.lastModifiedBy.get.id shouldBe "suspendingMember"
            state.meta.memberStatus shouldBe MEMBER_STATUS_SUSPENDED          }
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

            val memberTerminated = result.reply.getValue.asMessage.sealedValue.memberEventValue.get
              .memberEvent.asMessage.sealedValue.memberTerminated.get

            memberTerminated.memberId shouldBe Some(
              MemberId(testMemberIdString)
            )
            memberTerminated.lastMeta.get.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
            memberTerminated.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val event = result.eventOfType[MemberTerminated]
            event.memberId.get.id shouldBe testMemberIdString
            event.lastMeta.get.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
            event.lastMeta.get.lastModifiedBy.get.id shouldBe "terminatingMember"

            val state = result.stateOfType[TerminatedMemberState]

            state.lastMeta.createdBy.get.id shouldBe "registeringMember"
            state.lastMeta.lastModifiedBy.get.id shouldBe "terminatingMember"
            state.lastMeta.memberStatus shouldBe MEMBER_STATUS_SUSPENDED
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

            result.reply.getError.getMessage shouldBe "Cannot edit info for suspended members"
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

          result.reply.getError.getMessage shouldBe "Terminated members cannot process messages"
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

          result.reply.getError.getMessage shouldBe "Terminated members cannot process messages"
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

          result.reply.getError.getMessage shouldBe "Terminated members cannot process messages"
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

          result.reply.getError.getMessage shouldBe "Terminated members cannot process messages"
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

          result.reply.getError.getMessage shouldBe "Terminated members cannot process messages"
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

          result.reply.getError.getMessage shouldBe "Terminated members cannot process messages"
        }
      }
    }
  }

}