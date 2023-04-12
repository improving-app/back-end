package com.improving.app.gateway.domain

import com.improving.app.gateway.domain.common.IdTypes.{MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.common.Contact
import io.circe.{Decoder, Encoder}

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

sealed trait NotificationPreference

object NotificationPreference {

  case object EMAIL_NOTIFICATION_PREFERENCE extends NotificationPreference

  case object SMS_NOTIFICATION_PREFERENCE extends NotificationPreference

  case object APPLICATION_NOTIFICATION_PREFERENCE extends NotificationPreference

  implicit val decodeNotificationPreference: Decoder[NotificationPreference] = Decoder[String].emap {
    case "EMAIL_NOTIFICATION_PREFERENCE"       => Right(EMAIL_NOTIFICATION_PREFERENCE)
    case "SMS_NOTIFICATION_PREFERENCE"         => Right(SMS_NOTIFICATION_PREFERENCE)
    case "APPLICATION_NOTIFICATION_PREFERENCE" => Right(APPLICATION_NOTIFICATION_PREFERENCE)
    case other                                 => Left(s"Invalid NotificationPreference: $other")
  }

  implicit val notificationPreferenceEncoder: Encoder[NotificationPreference] =
    Encoder[String].contramap {
      case EMAIL_NOTIFICATION_PREFERENCE       => "EMAIL_NOTIFICATION_PREFERENCE"
      case SMS_NOTIFICATION_PREFERENCE         => "SMS_NOTIFICATION_PREFERENCE"
      case APPLICATION_NOTIFICATION_PREFERENCE => "APPLICATION_NOTIFICATION_PREFERENCE"
    }
}

sealed trait MemberStatus
object MemberStatus {
  case object DRAFT_MEMBER_STATUS extends MemberStatus

  case object ACTIVE_MEMBER_STATUS extends MemberStatus

  case object SUSPENDED_MEMBER_STATUS extends MemberStatus

  implicit val decodeMemberStatus: Decoder[MemberStatus] = Decoder[String].emap {
    case "DRAFT_MEMBER_STATUS"     => Right(DRAFT_MEMBER_STATUS)
    case "ACTIVE_MEMBER_STATUS"    => Right(ACTIVE_MEMBER_STATUS)
    case "SUSPENDED_MEMBER_STATUS" => Right(SUSPENDED_MEMBER_STATUS)
    case other                     => Left(s"Invalid NotificationPreference: $other")
  }

  implicit val memberStatusEncoder: Encoder[MemberStatus] =
    Encoder[String].contramap {
      case DRAFT_MEMBER_STATUS     => "DRAFT_MEMBER_STATUS"
      case ACTIVE_MEMBER_STATUS    => "ACTIVE_MEMBER_STATUS"
      case SUSPENDED_MEMBER_STATUS => "SUSPENDED_MEMBER_STATUS"
    }
}

case class MemberMetaInfo(
    createdOn: Instant,
    createdBy: MemberId,
    lastUpdated: Instant,
    lastUpdatedBy: MemberId,
    status: MemberStatus
)
