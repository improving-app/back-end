package com.improving.app.gateway.domain.common

import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.MemberMessages.{MemberData, MemberEventResponse, MemberRegistered}
import com.improving.app.gateway.domain.MemberStatus.{
  ACTIVE_MEMBER_STATUS,
  INACTIVE_MEMBER_STATUS,
  INITIAL_MEMBER_STATUS,
  SUSPENDED_MEMBER_STATUS,
  TERMINATED_MEMBER_STATUS
}
import com.improving.app.gateway.domain.NotificationPreference.{
  APPLICATION_NOTIFICATION_PREFERENCE,
  EMAIL_NOTIFICATION_PREFERENCE,
  SMS_NOTIFICATION_PREFERENCE
}
import com.improving.app.gateway.domain.common.{Contact => GatewayContact}
import com.improving.app.gateway.domain.{
  MemberInfo => GatewayMemberInfo,
  MemberMetaInfo => GatewayMemberMetaInfo,
  MemberStatus => GatewayMemberStatus,
  NotificationPreference => GatewayNotificationPreference
}
import com.improving.app.member.domain.{MemberInfo, MemberMetaInfo, MemberStatus, NotificationPreference}
import com.typesafe.config.ConfigFactory

import java.time.Instant
import java.util.UUID

object util {

  def getHostAndPortForService(serviceName: String): (String, Int) = {
    val config = ConfigFactory.load("application.conf")
    (
      config.getString(s"services.$serviceName.host"),
      config.getInt(s"services.$serviceName.port")
    )
  }

  def gatewayMemberInfoToMemberInfo(info: GatewayMemberInfo): MemberInfo = MemberInfo(
    handle = info.handle,
    avatarUrl = info.avatarUrl,
    firstName = info.firstName,
    lastName = info.lastName,
    notificationPreference = gatewayNotificationPreferenceToNotificationPreference(info.notificationPreference),
    notificationOptIn = info.notificationOptIn,
    contact = Some(gatewayContactToContact(info.contact)),
    organizations = info.organizations.map(id => OrganizationId(id.toString)),
    tenant = Some(TenantId(info.tenant.toString))
  )

  def memberInfoToGatewayMemberInfo(info: MemberInfo): GatewayMemberInfo = GatewayMemberInfo(
    handle = info.handle,
    avatarUrl = info.avatarUrl,
    firstName = info.firstName,
    lastName = info.lastName,
    notificationPreference = notificationPreferenceToGatewayNotificationPreference(info.notificationPreference),
    notificationOptIn = info.notificationOptIn,
    contact = contactToGatewayContact(info.contact.getOrElse(Contact.defaultInstance)),
    organizations = info.organizations.map(id => UUID.fromString(id.id)),
    tenant = UUID.fromString(info.tenant.getOrElse(TenantId.defaultInstance).id)
  )

  private def gatewayContactToContact(contact: GatewayContact): Contact = Contact(
    firstName = contact.firstName,
    lastName = contact.lastName,
    emailAddress = contact.emailAddress,
    phone = contact.phone,
    userName = contact.userName
  )

  private def contactToGatewayContact(contact: Contact): GatewayContact = GatewayContact(
    firstName = contact.firstName,
    lastName = contact.lastName,
    emailAddress = contact.emailAddress,
    phone = contact.phone,
    userName = contact.userName
  )

  private def memberStateToGatewayMemberState(memberStatus: MemberStatus): GatewayMemberStatus =
    if (memberStatus.isMemberStatusInitial) INITIAL_MEMBER_STATUS
    else if (memberStatus.isMemberStatusActive) ACTIVE_MEMBER_STATUS
    else if (memberStatus.isMemberStatusInactive) INACTIVE_MEMBER_STATUS
    else if (memberStatus.isMemberStatusSuspended) SUSPENDED_MEMBER_STATUS
    else TERMINATED_MEMBER_STATUS

  private def memberMetaToGatewayMemberMeta(info: MemberMetaInfo): GatewayMemberMetaInfo = GatewayMemberMetaInfo(
    createdOn = Instant.ofEpochSecond(info.createdOn.map(_.seconds).getOrElse(0L)),
    createdBy = UUID.fromString(info.createdBy.getOrElse(MemberId.defaultInstance).id),
    lastUpdated = Instant.ofEpochSecond(info.lastModifiedOn.map(_.seconds).getOrElse(0L)),
    lastUpdatedBy = UUID.fromString(info.lastModifiedBy.getOrElse(MemberId.defaultInstance).id),
    status = memberStateToGatewayMemberState(info.memberState)
  )

  private def notificationPreferenceToGatewayNotificationPreference(
      notificationPreference: NotificationPreference
  ): GatewayNotificationPreference = if (notificationPreference.isNotificationPreferenceEmail)
    EMAIL_NOTIFICATION_PREFERENCE
  else if (notificationPreference.isNotificationPreferenceSms) SMS_NOTIFICATION_PREFERENCE
  else APPLICATION_NOTIFICATION_PREFERENCE

  private def gatewayNotificationPreferenceToNotificationPreference(
      notificationPreference: GatewayNotificationPreference
  ): NotificationPreference = notificationPreference match {
    case EMAIL_NOTIFICATION_PREFERENCE =>
      NotificationPreference.fromValue(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL.value)
    case SMS_NOTIFICATION_PREFERENCE =>
      NotificationPreference.fromValue(NotificationPreference.NOTIFICATION_PREFERENCE_SMS.value)
    case APPLICATION_NOTIFICATION_PREFERENCE =>
      NotificationPreference.fromValue(NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION.value)
  }

  def memberResponseToGatewayEventResponse(
      response: com.improving.app.member.domain.MemberResponse
  ): MemberEventResponse =
    if (response.asMessage.sealedValue.isMemberEventValue)
      response.asMessage.getMemberEventValue.memberEvent match {
        case response @ com.improving.app.member.domain.MemberRegistered(_, _, _, _, _) =>
          MemberRegistered(
            UUID.fromString(response.memberId.getOrElse(MemberId.defaultInstance).id),
            memberInfoToGatewayMemberInfo(response.memberInfo.getOrElse(MemberInfo.defaultInstance)),
            UUID.fromString(response.actingMember.getOrElse(MemberId.defaultInstance).id),
            response.eventTime.getOrElse(Timestamp.defaultInstance).asJavaInstant
          )
        //  case response @ com.improving.app.member.domain.MemberActivated(_, _, _, _) =>
        //    Some(
        //      MemberResponse.of(
        //        MemberActivated(
        //          response.memberId,
        //          response.actingMember,
        //          response.eventTime
        //        )
        //      )
        //    )
        //  case response @ com.improving.app.member.domain.MemberInactivated(_, _, _, _) =>
        //    Some(
        //      MemberResponse.of(
        //        MemberInactivated(
        //          response.memberId,
        //          response.actingMember,
        //          response.eventTime
        //        )
        //      )
        //    )
        //  case response @ com.improving.app.member.domain.MemberSuspended(_, _, _, _) =>
        //    Some(
        //      MemberResponse.of(
        //        MemberSuspended(
        //          response.memberId,
        //          response.actingMember,
        //          response.eventTime
        //        )
        //      )
        //    )
        //  case response @ com.improving.app.member.domain.MemberTerminated(_, _, _, _) =>
        //    Some(
        //      MemberResponse(
        //        MemberTerminated(
        //          response.memberId,
        //          response.actingMember,
        //          response.eventTime
        //        )
        //      )
        //    )
        //  case response @ com.improving.app.member.domain.MemberInfoUpdated(_, _, _, _, _) =>
        //    Some(
        //      MemberResponse(
        //        MemberInfoUpdated(
        //          response.memberId,
        //          response.memberInfo.map(memberInfoToGatewayMemberInfo),
        //          response.actingMember,
        //          response.eventTime
        //        )
        //      )
        //    )
        //  case _ => None
      }
    else {
      val data = response.asMessage.getMemberStateValue
      MemberData(
        UUID.fromString(data.memberId.getOrElse(MemberId.defaultInstance).id),
        memberInfoToGatewayMemberInfo(data.memberInfo.getOrElse(MemberInfo.defaultInstance)),
        memberMetaToGatewayMemberMeta(data.memberMetaInfo.getOrElse(MemberMetaInfo.defaultInstance))
      )
    }
}
