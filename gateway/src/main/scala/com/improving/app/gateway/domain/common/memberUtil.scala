package com.improving.app.gateway.domain.common

import com.improving.app.common.domain.util.{ContactUtil, EditableContactUtil}
import com.improving.app.member.domain.{MemberMetaInfo, MemberState, NotificationPreference, EditableInfo => EditableMemberInfo}
import com.typesafe.config.ConfigFactory
import com.improving.app.gateway.domain.member.{EditableMemberInfo => GatewayEditableMemberInfo, MemberMetaInfo => GatewayMemberMetaInfo, MemberStates => GatewayMemberStates, NotificationPreference => GatewayNotificationPreference}

object memberUtil {

  def gatewayEditableMemberInfoToEditableInfo(info: GatewayEditableMemberInfo): EditableMemberInfo = EditableMemberInfo(
    info.handle,
    info.avatarUrl,
    info.firstName,
    info.lastName,
    info.notificationPreference.map(gatewayNotificationPreferenceToNotificationPreference),
    info.contact.map(_.toEditable),
    info.organizationMembership,
    info.tenant
  )

  def editableMemberInfoToGatewayEditableInfo(info: EditableMemberInfo): GatewayEditableMemberInfo =
    GatewayEditableMemberInfo(
      info.handle,
      info.avatarUrl,
      info.firstName,
      info.lastName,
      info.notificationPreference.map(notificationPreferenceToGatewayNotificationPreference),
      info.contact.map(_.toContact),
      info.organizationMembership,
      info.tenant
    )

  private def memberStateToGatewayMemberState(memberStatus: MemberState): GatewayMemberStates = {
    if (memberStatus.isMemberStateDraft) GatewayMemberStates.MEMBER_STATES_DRAFT
    else if (memberStatus.isMemberStateActive) GatewayMemberStates.MEMBER_STATES_ACTIVE
    else GatewayMemberStates.MEMBER_STATES_SUSPENDED
  }

  def memberMetaToGatewayMemberMeta(meta: MemberMetaInfo): GatewayMemberMetaInfo = GatewayMemberMetaInfo(
    meta.createdOn,
    meta.createdBy,
    meta.lastModifiedOn,
    meta.lastModifiedBy,
    memberStateToGatewayMemberState(meta.currentState)
  )

  def notificationPreferenceToGatewayNotificationPreference(
      notificationPreference: NotificationPreference
  ): GatewayNotificationPreference =
    if (notificationPreference.isNotificationPreferenceEmail)
      GatewayNotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
    else if (notificationPreference.isNotificationPreferenceSms)
      GatewayNotificationPreference.NOTIFICATION_PREFERENCE_SMS
    else GatewayNotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION

  private def gatewayNotificationPreferenceToNotificationPreference(
      notificationPreference: GatewayNotificationPreference
  ): NotificationPreference = notificationPreference match {
    case GatewayNotificationPreference.NOTIFICATION_PREFERENCE_EMAIL =>
      NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
    case GatewayNotificationPreference.NOTIFICATION_PREFERENCE_SMS =>
      NotificationPreference.NOTIFICATION_PREFERENCE_SMS
    case _ =>
      NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION
  }
}
