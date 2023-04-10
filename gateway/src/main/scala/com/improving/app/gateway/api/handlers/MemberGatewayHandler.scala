package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.MemberMessages._
import com.improving.app.gateway.domain.common.util.{
  gatewayMemberInfoToMemberInfo,
  getHostAndPortForService,
  memberResponseToGatewayEventResponse
}
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.member.domain.MemberEventResponse

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MemberGatewayHandler(implicit val system: ActorSystem[_]) {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  private val (clientHost, clientPort) = getHostAndPortForService("member-service")

  private val memberClient = MemberServiceClient(
    GrpcClientSettings
      .connectToServiceAt(clientHost, clientPort)
      .withTls(false)
  )

  def registerMember(in: RegisterMember): Future[MemberRegistered] = memberClient
    .registerMember(
      com.improving.app.member.domain.RegisterMember(
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
