package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.common.domain.MemberId
import com.improving.app.gateway.domain.{
  MemberMetaInfo,
  MemberRegistered,
  MemberStates,
  MemberInfo => GatewayMemberInfo,
  NotificationPreference => GatewayNotificationPreference,
  RegisterMember => GatewayRegisterMember
}
import com.improving.app.gateway.domain.common.util.{
  gatewayMemberInfoToMemberInfo,
  getHostAndPortForService,
  memberInfoToGatewayMemberInfo,
  memberMetaToGatewayMemberMeta
}
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.member.domain.{MemberInfo, NotificationPreference, RegisterMember}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MemberGatewayHandler(grpcClientSettingsOpt: Option[GrpcClientSettings] = None)(implicit
    val system: ActorSystem[_]
) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = getHostAndPortForService("member-service")

  private val memberClient: MemberServiceClient = MemberServiceClient(
    grpcClientSettingsOpt.getOrElse(
      GrpcClientSettings
        .connectToServiceAt(clientHost, clientPort)
        .withTls(false)
    )
  )

  def registerMember(in: GatewayRegisterMember): Future[MemberRegistered] = {
    memberClient
      .registerMember(
        RegisterMember(in.memberId, in.memberInfo.map(gatewayMemberInfoToMemberInfo), in.registeringMember)
      )
      .map { response =>
        MemberRegistered(
          response.memberId,
          response.memberInfo.map(memberInfoToGatewayMemberInfo),
          response.meta.map(memberMetaToGatewayMemberMeta)
        )
      }
  }
}
