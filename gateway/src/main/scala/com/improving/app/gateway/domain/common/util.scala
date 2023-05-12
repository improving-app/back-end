package com.improving.app.gateway.domain.common

import com.improving.app.member.domain.{MemberInfo, MemberMetaInfo, MemberState, NotificationPreference}
import com.typesafe.config.ConfigFactory
import com.improving.app.gateway.domain.{
  MemberStates => GatewayMemberStates,
  MemberInfo => GatewayMemberInfo,
  MemberMetaInfo => GatewayMemberMetaInfo,
  NotificationPreference => GatewayNotificationPreference
}

object util {

  def getHostAndPortForService(serviceName: String): (String, Int) = {
    val config = ConfigFactory
      .load("application.conf")
      .withFallback(ConfigFactory.defaultApplication())
    (
      config.getString(s"services.$serviceName.host"),
      config.getInt(s"services.$serviceName.port")
    )
  }

  def gatewayMemberInfoToMemberInfo(info: GatewayMemberInfo): MemberInfo =
    MemberInfo(
      info.handle,
      info.avatarUrl,
      info.firstName,
      info.lastName,
      info.notificationPreference
        .map(pref => NotificationPreference.fromValue(pref.value)),
      info.notificationOptIn,
      info.contact,
      info.organizationMembership,
      info.tenant
    )

  def memberInfoToGatewayMemberInfo(info: MemberInfo): GatewayMemberInfo = GatewayMemberInfo(
    info.handle,
    info.avatarUrl,
    info.firstName,
    info.lastName,
    info.notificationPreference.map(notificationPreferenceToGatewayNotificationPreference),
    info.notificationOptIn,
    info.contact,
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

  private def notificationPreferenceToGatewayNotificationPreference(
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
