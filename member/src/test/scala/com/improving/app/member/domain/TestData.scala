package com.improving.app.member.domain

import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}

import java.util.UUID

object TestData {
  val testMemberIdString: String = UUID.randomUUID().toString

  val baseContact: Contact = Contact(
    firstName = "firstName",
    lastName = "lastName",
    emailAddress = Some("email@email.com"),
    phone = Some("111-111-1111"),
    userName = "userName"
  )

  val editContact: Contact = Contact(
    firstName = "editFirstName",
    lastName = "editLastName",
    emailAddress = Some("editEmail@email.com"),
    phone = Some("222-222-2222"),
    userName = "editUserName"
  )

  val baseMemberInfo: MemberInfo = MemberInfo(
    handle = "handle",
    avatarUrl = "avatarUrl",
    firstName = "firstName",
    lastName = "lastName",
    notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL),
    notificationOptIn = true,
    contact = baseContact,
    organizationMembership = Seq(OrganizationId(UUID.randomUUID().toString)),
    tenant = TenantId(UUID.randomUUID().toString)
  )

  val baseEditableInfo: EditableInfo = EditableInfo(
    handle = Some("editHandle"),
    avatarUrl = Some("editAvatarUrl"),
    firstName = Some("editFirstName"),
    lastName = Some("editLastName"),
    notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
    contact = Some(editContact),
    organizationMembership = Seq(OrganizationId("editOrg1")),
    tenant = Some(TenantId("editTenantId"))
  )

  val baseRegisterMember: RegisterMember = RegisterMember(
    memberId = MemberId(testMemberIdString),
    memberInfo = baseMemberInfo,
    registeringMember = MemberId("registeringMember")
  )

  val baseActivateMember: ActivateMember = ActivateMember(
    memberId = MemberId(testMemberIdString),
    activatingMember = MemberId("activatingMember")
  )

  val baseSuspendMember: SuspendMember = SuspendMember(
    memberId = MemberId(testMemberIdString),
    suspendingMember = MemberId("suspendingMember")
  )

  val baseTerminateMember: TerminateMember = TerminateMember(
    memberId = MemberId(testMemberIdString),
    terminatingMember = MemberId("terminatingMember")
  )

  val baseEditMemberInfo: EditMemberInfo = EditMemberInfo(
    memberId = MemberId(testMemberIdString),
    memberInfo = baseEditableInfo,
    editingMember = MemberId("editingMember")
  )

  val baseGetMemberInfo: GetMemberInfo = GetMemberInfo(
    memberId = MemberId(testMemberIdString)
  )
}
