package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.MemberMessages._
import com.improving.app.gateway.domain.common.util.{
  gatewayMemberInfoToMemberInfo,
  memberResponseToGatewayEventResponse
}
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.member.domain.MemberEventResponse
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MemberGatewayHandler(hostAndPort: (String, Int))(implicit val system: ActorSystem[_]) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = hostAndPort

  private val memberClient: MemberServiceClient = MemberServiceClient(
    GrpcClientSettings
      .connectToServiceAt(clientHost, clientPort)
      .withTls(false)
  )

  def registerMember(in: RegisterMember): Future[MemberRegistered] = {
    logger.info(
      com.improving.app.member.domain
        .RegisterMember(
          Some(com.improving.app.common.domain.MemberId.of(in.memberId.toString)),
          Some(gatewayMemberInfoToMemberInfo(in.memberInfo)),
          Some(com.improving.app.common.domain.MemberId.of(in.actingMember.toString))
        )
        .toProtoString
    )
    memberClient
      .registerMember(
        com.improving.app.member.domain.RegisterMember(
          Some(com.improving.app.common.domain.MemberId.of(in.memberId.toString)),
          Some(gatewayMemberInfoToMemberInfo(in.memberInfo)),
          Some(com.improving.app.common.domain.MemberId.of(in.actingMember.toString))
        )
      )
      .map(response =>
        memberResponseToGatewayEventResponse(
          MemberEventResponse.of(response)
        ).asInstanceOf[MemberRegistered]
      )
  }
}
