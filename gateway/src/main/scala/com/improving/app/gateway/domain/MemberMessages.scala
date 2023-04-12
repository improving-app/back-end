package com.improving.app.gateway.domain

import com.improving.app.gateway.domain.common.IdTypes.MemberId
import java.time.Instant

object MemberMessages {

  sealed trait MemberCommand

  sealed trait MemberResponse extends MemberEventResponse
  sealed trait MemberEventResponse

  case class ErrorResponse(msg: String) extends MemberResponse

  case class RegisterMember(
      memberId: MemberId,
      memberInfo: MemberInfo,
      actingMember: MemberId
  ) extends MemberCommand

  case class MemberRegistered(
      memberId: MemberId,
      memberInfo: MemberInfo,
      metaInfo: MemberMetaInfo
  ) extends MemberResponse

  case class MemberData(
      memberId: MemberId,
      memberInfo: MemberInfo,
      memberMetaInfo: MemberMetaInfo
  ) extends MemberEventResponse
}
