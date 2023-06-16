package com.improving.app.gateway.domain.common

import com.improving.app.gateway.domain.member.{
  EditableMemberInfo => GatewayEditableMemberInfo,
  MemberMetaInfo => GatewayMemberMetaInfo,
  MemberStates => GatewayMemberStates,
  NotificationPreference => GatewayNotificationPreference
}
import com.improving.app.member.domain.{
  MemberMetaInfo,
  MemberState,
  NotificationPreference,
  EditableInfo => EditableMemberInfo
}
import com.typesafe.config.ConfigFactory

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
}
