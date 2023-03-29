package com.improving.app.gateway.domain

import com.improving.app.member.domain.{MemberInfo, MemberMetaInfo}
import com.typesafe.config.ConfigFactory

object util {

  def getHostAndPortForService(serviceName: String): (String, Int) = {
    val config = ConfigFactory.load("application.conf")
    (
      config.getString(s"app.improving.akka.grpc.$serviceName.client-url"),
      config.getInt(s"app.improving.akka.grpc.$serviceName.client-url-port")
    )
  }

  def gatewayMemberInfoToMemberInfo(info: Info): MemberInfo = MemberInfo(
    handle = info.handle,
    avatarUrl = info.avatarUrl,
    firstName = info.firstName,
    lastName = info.lastName,
    notificationPreference =
      com.improving.app.member.domain.NotificationPreference.fromValue(info.notificationPreference.value),
    notificationOptIn = info.notificationOptIn,
    contact = info.contact,
    organizations = info.organizations,
    tenant = info.tenant
  )

  private def memberMetaToGatewayMemberMeta(info: MemberMetaInfo): MetaInfo = MetaInfo(
    createdOn = info.createdOn,
    createdBy = info.createdBy,
    lastUpdated = info.lastModifiedOn,
    lastUpdatedBy = info.lastModifiedBy,
    status = MemberStatus.fromValue(info.memberState.value)
  )

  private def memberInfoToGatewayMemberInfo(info: MemberInfo): Info = Info(
    handle = info.handle,
    avatarUrl = info.avatarUrl,
    firstName = info.firstName,
    lastName = info.lastName,
    notificationPreference = NotificationPreference.fromValue(info.notificationPreference.value),
    notificationOptIn = info.notificationOptIn,
    contact = info.contact,
    organizations = info.organizations,
    tenant = info.tenant,
  )

  def memberResponseToGatewayEventResponse(
      response: com.improving.app.member.domain.MemberResponse
  ): MemberGatewayResponse =
    if (response.asMessage.sealedValue.isMemberEventValue)
      response.asMessage.getMemberEventValue.memberEvent match {
        case response @ com.improving.app.member.domain.MemberRegistered(_, _, _, _, _) =>
          MemberGatewayEventResponse.of(
            MemberRegistered(
              response.memberId,
              response.memberInfo.map(memberInfoToGatewayMemberInfo),
              response.actingMember,
              response.eventTime
            )
          )
        case response @ com.improving.app.member.domain.MemberActivated(_, _, _, _) =>
          MemberGatewayEventResponse.of(
            MemberActivated(
              response.memberId,
              response.actingMember,
              response.eventTime
            )
          )
        case response @ com.improving.app.member.domain.MemberInactivated(_, _, _, _) =>
          MemberGatewayEventResponse.of(
            MemberInactivated(
              response.memberId,
              response.actingMember,
              response.eventTime
            )
          )
        case response @ com.improving.app.member.domain.MemberSuspended(_, _, _, _) =>
          MemberGatewayEventResponse.of(
            MemberSuspended(
              response.memberId,
              response.actingMember,
              response.eventTime
            )
          )
        case response @ com.improving.app.member.domain.MemberTerminated(_, _, _, _) =>
          MemberGatewayEventResponse.of(
            MemberTerminated(
              response.memberId,
              response.actingMember,
              response.eventTime
            )
          )
        case response @ com.improving.app.member.domain.MemberInfoUpdated(_, _, _, _, _) =>
          MemberGatewayEventResponse.of(
            MemberInfoUpdated(
              response.memberId,
              response.memberInfo.map(memberInfoToGatewayMemberInfo),
              response.actingMember,
              response.eventTime
            )
          )
      }
    else {
      val data = response.asMessage.getMemberStateValue
      MemberData(
        data.memberId,
        data.memberInfo.map(memberInfoToGatewayMemberInfo),
        data.memberMetaInfo.map(memberMetaToGatewayMemberMeta)
      )
    }
}
