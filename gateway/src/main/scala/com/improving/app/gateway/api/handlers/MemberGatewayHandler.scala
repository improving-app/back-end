package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.{MemberRegistered, RegisterMember => GatewayRegisterMember}
import com.improving.app.gateway.domain.common.util.{
  editableMemberInfoToGatewayEditableInfo,
  gatewayEditableMemberInfoToEditableInfo,
  getHostAndPortForService,
  memberMetaToGatewayMemberMeta
}
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.member.domain.RegisterMember
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
    logger.info(
      RegisterMember(
        in.memberId,
        Some(gatewayEditableMemberInfoToEditableInfo(in.getMemberInfo)),
        in.registeringMember
      ).toProtoString
    )
    memberClient
      .registerMember(
        RegisterMember(
          in.memberId,
          Some(gatewayEditableMemberInfoToEditableInfo(in.getMemberInfo)),
          in.registeringMember
        )
      )
      .map { response =>
        logger.info(
          MemberRegistered(
            response.memberId,
            response.memberInfo.map(editableMemberInfoToGatewayEditableInfo),
            response.meta.map(memberMetaToGatewayMemberMeta)
          ).toProtoString
        )
        MemberRegistered(
          response.memberId,
          response.memberInfo.map(editableMemberInfoToGatewayEditableInfo),
          response.meta.map(memberMetaToGatewayMemberMeta)
        )
      }
  }
}
