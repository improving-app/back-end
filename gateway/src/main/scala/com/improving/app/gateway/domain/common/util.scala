package com.improving.app.gateway.domain.common

import com.improving.app.member.domain.{MemberInfo, MemberMetaInfo, NotificationPreference}
import com.typesafe.config.ConfigFactory
import com.improving.app.gateway.domain.{
  MemberStates,
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
    info.notificationPreference
      .map(pref => GatewayNotificationPreference.fromValue(pref.value)),
    info.notificationOptIn,
    info.contact,
    info.organizationMembership,
    info.tenant
  )

  def memberMetaToGatewayMemberMeta(meta: MemberMetaInfo): GatewayMemberMetaInfo = GatewayMemberMetaInfo(
    meta.createdOn,
    meta.createdBy,
    meta.lastModifiedOn,
    meta.lastModifiedBy,
    MemberStates.fromValue(meta.currentState.value)
  )
}
