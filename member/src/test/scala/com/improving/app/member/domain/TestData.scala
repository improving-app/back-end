package com.improving.app.member.domain

import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}

object TestData {
  val testMemberIdString = "memberId"

  val baseContact = Contact(
    firstName = "firstName",
    lastName = "lastName",
    emailAddress = Some("email@email.com"),
    phone = Some("111-111-1111"),
    userName = "userName"
  )

  val editContact = Contact(
    firstName = "editFirstName",
    lastName = "editLastName",
    emailAddress = Some("editEmail@email.com"),
    phone = Some("222-222-2222"),
    userName = "editUserName"
  )

  val baseMemberInfo = MemberInfo(
    handle = "handle",
    avatarUrl = "avatarUrl",
    firstName = "firstName",
    lastName = "lastName",
    notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL),
    notificationOptIn = true,
    contact = Some(baseContact),
    organizationMembership = Seq(OrganizationId("org1")),
    tenant = Some(TenantId("tenantId"))
  )

  val baseEditableInfo = EditableInfo(
    handle = Some("editHandle"),
    avatarUrl = Some("editAvatarUrl"),
    firstName = Some("editFirstName"),
    lastName = Some("editLastName"),
    notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_SMS),
    contact = Some(editContact),
    organizationMembership = Seq(OrganizationId("editOrg1")),
    tenant = Some(TenantId("editTenantId"))
  )

  val baseRegisterMember = RegisterMember(
    memberId = Some(
      MemberId(testMemberIdString)
    ),
    memberInfo = Some(baseMemberInfo),
    registeringMember = Some(
      MemberId("registeringMember")
    )
  )

  val baseActivateMember = ActivateMember(
    memberId = Some(
      MemberId(testMemberIdString)
    ),
    activatingMember = Some(
      MemberId("activatingMember")
    )
  )

  val baseSuspendMember = SuspendMember(
    memberId = Some(
      MemberId(testMemberIdString)
    ),
    suspendingMember = Some(
      MemberId("suspendingMember")
    )
  )

  val baseTerminateMember = TerminateMember(
    memberId = Some(
      MemberId(testMemberIdString)
    ),
    terminatingMember = Some(
      MemberId("terminatingMember")
    )
  )

  val baseEditMemberInfo = EditMemberInfo(
    memberId = Some(
      MemberId(testMemberIdString)
    ),
    memberInfo = Some(baseEditableInfo),
    editingMember = Some(
      MemberId("editingMember")
    )
  )

  val baseGetMemberInfo = GetMemberInfo(
    memberId = Some(
      MemberId(testMemberIdString)
    )
  )
}
