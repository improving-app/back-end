package com.improving.app.gateway.domain

import com.improving.app.common.domain.util.{ContactUtil, EditableContactUtil}
import com.improving.app.gateway.domain.member.{
  EditableMemberInfo => GatewayEditableMemberInfo,
  MemberMetaInfo => GatewayMemberMetaInfo,
  MemberStates => GatewayMemberStates,
  NotificationPreference => GatewayNotificationPreference
}
import com.improving.app.member.domain.{
  EditableInfo => EditableMemberInfo,
  MemberMetaInfo,
  MemberState,
  NotificationPreference
}

object memberUtil {

  implicit class GatewayEditableMemberInfoUtil(info: GatewayEditableMemberInfo) {
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
