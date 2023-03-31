package com.improving.app.gateway.domain

import com.improving.app.gateway.domain.common.IdTypes.MemberId

import java.time.Instant

object MemberMessages {

  trait MemberCommand
  trait MemberResponse extends MemberEventResponse
  trait MemberEventResponse

  case class RegisterMember(
      memberInfo: MemberInfo,
      actingMember: MemberId
  ) extends MemberCommand

  case class MemberRegistered(
      memberId: MemberId,
      memberInfo: MemberInfo,
      actingMember: MemberId,
      eventTime: Instant
  ) extends MemberResponse

}
