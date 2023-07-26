package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.member.{
  ActivateMember => GatewayActivateMember,
  MemberActivated,
  MemberRegistered,
  MemberTerminated,
  RegisterMember => GatewayRegisterMember,
  TerminateMember => GatewayTermainateMember
}
import com.improving.app.gateway.domain.memberUtil.{
  EditableMemberInfoUtil,
  GatewayEditableMemberInfoUtil,
  MemberMetaUtil
}
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.member.domain.{ActivateMember, RegisterMember, TerminateMember}
import com.typesafe.scalalogging.StrictLogging
import com.improving.app.gateway.api.handlers.errors.handlers.exceptionHandler

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

  def registerMember(in: GatewayRegisterMember): Future[MemberRegistered] =
    memberClient
      .registerMember(
        RegisterMember(
          in.memberId,
          Some(in.getMemberInfo.toEditableInfo),
          in.onBehalfOf
        )
      )
      .map { response =>
        MemberRegistered(
          response.memberId,
          response.memberInfo.map(_.toGatewayEditableInfo),
          response.meta.map(_.toGatewayMemberMeta)
        )
      }

  def activateMember(in: GatewayActivateMember): Future[MemberActivated] =
    memberClient
      .activateMember(
        ActivateMember(
          in.memberId,
          in.onBehalfOf
        )
      )
      .map { response =>
        MemberActivated(
          response.memberId,
          response.meta.map(_.toGatewayMemberMeta)
        )
      }

  def terminateMember(in: GatewayTermainateMember): Future[MemberTerminated] =
    memberClient
      .terminateMember(
        TerminateMember(
          in.memberId,
          in.onBehalfOf
        )
      )
      .map { response =>
        MemberTerminated(
          response.memberId,
          response.lastMeta.map(_.toGatewayMemberMeta)
        )
      }
}
