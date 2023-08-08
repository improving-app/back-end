package com.improving.app.gateway.domain

import com.improving.app.common.domain.util.EditableContactUtil
import com.improving.app.gateway.domain.demoScenario.Member
import com.improving.app.gateway.domain.member.{
  EditableMemberInfo => GatewayEditableMemberInfo,
  MemberInfo => GatewayMemberInfo,
  MemberMetaInfo => GatewayMemberMetaInfo,
  MemberRegistered,
  MemberStates => GatewayMemberStates,
  NotificationPreference => GatewayNotificationPreference
}
import com.improving.app.member.domain.{
  EditableInfo => EditableMemberInfo,
  MemberInfo,
  MemberMetaInfo,
  MemberState,
  NotificationPreference
}

object memberUtil {

  implicit class MemberRegisteredUtil(established: MemberRegistered) {
    implicit def toMember: Member = Member(
      memberId = established.memberId,
      memberInfo = established.memberInfo.map(_.toInfo),
      metaInfo = established.meta
    )
  }

  implicit class GatewayEditableMemberInfoUtil(info: GatewayEditableMemberInfo) {

    def toInfo: GatewayMemberInfo = GatewayMemberInfo(
      handle = info.getHandle,
      avatarUrl = info.getAvatarUrl,
      firstName = info.getFirstName,
      lastName = info.getLastName,
      notificationPreference = info.getNotificationPreference,
      contact = info.contact.map(_.toContact),
      organizationMembership = info.organizationMembership,
      tenant = info.tenant
    )

    def toEditableInfo: EditableMemberInfo = EditableMemberInfo(
      info.handle,
      info.avatarUrl,
      info.firstName,
      info.lastName,
      info.notificationPreference.map(_.toNotificationPreference),
      info.contact,
      info.organizationMembership,
      info.tenant
    )
  }

  implicit class MemberInfoUtil(info: MemberInfo) {

    def toGateway: GatewayMemberInfo = GatewayMemberInfo(
      handle = info.handle,
      avatarUrl = info.avatarUrl,
      firstName = info.firstName,
      lastName = info.lastName,
      notificationPreference = info.getNotificationPreference.toGatewayNotificationPreference,
      contact = info.contact,
      organizationMembership = info.organizationMembership,
      tenant = info.tenant
    )
  }

  implicit class EditableMemberInfoUtil(info: EditableMemberInfo) {

    def toGatewayEditableInfo: GatewayEditableMemberInfo =
      GatewayEditableMemberInfo(
        info.handle,
        info.avatarUrl,
        info.firstName,
        info.lastName,
        info.notificationPreference.map(_.toGatewayNotificationPreference),
        info.contact,
        info.organizationMembership,
        info.tenant
      )
  }

  implicit class MemberStateUtil(memberState: MemberState) {
    def toGatewayMemberState: GatewayMemberStates = {
      if (memberState.isMemberStateDraft) GatewayMemberStates.MEMBER_STATES_DRAFT
      else if (memberState.isMemberStateActive) GatewayMemberStates.MEMBER_STATES_ACTIVE
      else GatewayMemberStates.MEMBER_STATES_SUSPENDED
    }
  }

  implicit class MemberMetaUtil(meta: MemberMetaInfo) {
    def toGatewayMemberMeta: GatewayMemberMetaInfo = GatewayMemberMetaInfo(
      meta.createdOn,
      meta.createdBy,
      meta.lastModifiedOn,
      meta.lastModifiedBy,
      meta.currentState.toGatewayMemberState
    )
  }

  implicit class NotificationPreferenceUtil(
      notificationPreference: NotificationPreference
  ) {

    def toGatewayNotificationPreference: GatewayNotificationPreference =
      if (notificationPreference.isNotificationPreferenceEmail)
        GatewayNotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
      else if (notificationPreference.isNotificationPreferenceSms)
        GatewayNotificationPreference.NOTIFICATION_PREFERENCE_SMS
      else GatewayNotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION
  }

  implicit class GatewayNotificationPreferenceUtil(
      notificationPreference: GatewayNotificationPreference
  ) {
    def toNotificationPreference: NotificationPreference = notificationPreference match {
      case GatewayNotificationPreference.NOTIFICATION_PREFERENCE_EMAIL =>
        NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
      case GatewayNotificationPreference.NOTIFICATION_PREFERENCE_SMS =>
        NotificationPreference.NOTIFICATION_PREFERENCE_SMS
      case _ =>
        NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION
    }
  }
}
