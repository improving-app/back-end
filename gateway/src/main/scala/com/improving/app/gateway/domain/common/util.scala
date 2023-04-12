package com.improving.app.gateway.domain.common

import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.MemberMessages.{
  ErrorResponse,
  MemberData,
  MemberEventResponse,
  MemberRegistered
}
import com.improving.app.gateway.domain.MemberStatus.{
  ACTIVE_MEMBER_STATUS,
  DRAFT_MEMBER_STATUS,
  SUSPENDED_MEMBER_STATUS
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
import com.improving.app.member.domain.{MemberInfo, MemberMetaInfo, MemberState, NotificationPreference}
import com.typesafe.config.ConfigFactory

import java.time.Instant
import java.util.UUID

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

  def gatewayMemberInfoToMemberInfo(info: GatewayMemberInfo): MemberInfo = MemberInfo(
    handle = info.handle,
    avatarUrl = info.avatarUrl,
    firstName = info.firstName,
    lastName = info.lastName,
    notificationPreference = Some(gatewayNotificationPreferenceToNotificationPreference(info.notificationPreference)),
    notificationOptIn = info.notificationOptIn,
    contact = Some(gatewayContactToContact(info.contact)),
    organizationMembership = info.organizations.map(id => OrganizationId(id.toString)),
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
    organizations = info.organizationMembership.map(id => UUID.fromString(id.id)),
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

  private def memberStateToGatewayMemberState(memberStatus: MemberState): GatewayMemberStatus = {
    if (memberStatus.isMemberStatusDraft) DRAFT_MEMBER_STATUS
    else if (memberStatus.isMemberStatusActive) ACTIVE_MEMBER_STATUS
    else SUSPENDED_MEMBER_STATUS
  }

  private def memberMetaToGatewayMemberMeta(info: MemberMetaInfo): GatewayMemberMetaInfo = GatewayMemberMetaInfo(
    createdOn = Instant.ofEpochSecond(info.createdOn.map(_.seconds).getOrElse(0L)),
    createdBy = UUID.fromString(info.createdBy.getOrElse(MemberId.defaultInstance).id),
    lastUpdated = Instant.ofEpochSecond(info.lastModifiedOn.map(_.seconds).getOrElse(0L)),
    lastUpdatedBy = UUID.fromString(info.lastModifiedBy.getOrElse(MemberId.defaultInstance).id),
    status = memberStateToGatewayMemberState(info.currentState)
  )

  private def notificationPreferenceToGatewayNotificationPreference(
      notificationPreferenceOpt: Option[NotificationPreference]
  ): GatewayNotificationPreference =
    notificationPreferenceOpt.fold[GatewayNotificationPreference](APPLICATION_NOTIFICATION_PREFERENCE)(
      notificationPreference =>
        if (notificationPreference.isNotificationPreferenceEmail) EMAIL_NOTIFICATION_PREFERENCE
        else if (notificationPreference.isNotificationPreferenceSms) SMS_NOTIFICATION_PREFERENCE
        else APPLICATION_NOTIFICATION_PREFERENCE
    )

  private def gatewayNotificationPreferenceToNotificationPreference(
      notificationPreference: GatewayNotificationPreference
  ): NotificationPreference = notificationPreference match {
    case EMAIL_NOTIFICATION_PREFERENCE =>
      NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
    case SMS_NOTIFICATION_PREFERENCE =>
      NotificationPreference.NOTIFICATION_PREFERENCE_SMS
    case APPLICATION_NOTIFICATION_PREFERENCE =>
      NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION
  }

  def memberResponseToGatewayEventResponse(
      response: com.improving.app.member.domain.MemberResponse
  ): MemberEventResponse =
    if (response.asMessage.sealedValue.isMemberEventValue) {
      if (response.asMessage.getMemberEventValue.memberEvent.asMessage.sealedValue.isMemberRegisteredValue) {
        val registered = response.asMessage.getMemberEventValue.memberEvent.asMessage.getMemberRegisteredValue
        MemberRegistered(
          UUID.fromString(registered.memberId.getOrElse(MemberId.defaultInstance).id),
          memberInfoToGatewayMemberInfo(registered.memberInfo.getOrElse(MemberInfo.defaultInstance)),
          memberMetaToGatewayMemberMeta(registered.meta.getOrElse(MemberMetaInfo.defaultInstance))
        )
      } else ErrorResponse("MemberEventResponse type is not yet implemented")
    } else {
      val data = response.asMessage.getMemberStateValue
      MemberData(
        UUID.fromString(data.memberId.getOrElse(MemberId.defaultInstance).id),
        memberInfoToGatewayMemberInfo(data.memberInfo.getOrElse(MemberInfo.defaultInstance)),
        memberMetaToGatewayMemberMeta(data.memberMetaInfo.getOrElse(MemberMetaInfo.defaultInstance))
      )
    }
}
