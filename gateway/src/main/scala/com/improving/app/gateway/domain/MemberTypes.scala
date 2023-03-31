package com.improving.app.gateway.domain

import com.improving.app.gateway.domain.MemberMessages.MemberEventResponse
import com.improving.app.gateway.domain.common.IdTypes.{MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.common.Contact

import java.time.Instant

case class MemberInfo(
    handle: String,
    avatarUrl: String,
    firstName: String,
    lastName: String,
    notificationPreference: NotificationPreference,
    notificationOptIn: Boolean,
    contact: Contact,
    organizations: Seq[OrganizationId],
    tenant: TenantId
)

trait NotificationPreference

case object EMAIL_NOTIFICATION_PREFERENCE extends NotificationPreference
case object SMS_NOTIFICATION_PREFERENCE extends NotificationPreference
case object APPLICATION_NOTIFICATION_PREFERENCE extends NotificationPreference

trait MemberStatus
case object INITIAL_MEMBER_STATUS extends MemberStatus
case object ACTIVE_MEMBER_STATUS extends MemberStatus
case object INACTIVE_MEMBER_STATUS extends MemberStatus
case object SUSPENDED_MEMBER_STATUS extends MemberStatus
case object TERMINATED_MEMBER_STATUS extends MemberStatus

case class MemberMetaInfo(
    createdOn: Instant,
    createdBy: MemberId,
    lastUpdated: Instant,
    lastUpdatedBy: MemberId,
    status: MemberStatus
)

case class MemberData(
    memberId: MemberId,
    memberInfo: MemberInfo,
    memberMetaInfo: MemberMetaInfo
) extends MemberEventResponse
