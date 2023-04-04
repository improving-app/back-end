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

  //override def activateMember(in: ActivateMember): Future[MemberActivated] =
  //  memberClient
  //    .activateMember(
  //      com.improving.app.member.domain.ActivateMember(
  //        in.memberId.map(id => com.improving.app.common.domain.MemberId(id.id)),
  //        in.actingMember.map(id => com.improving.app.common.domain.MemberId(id.id))
  //      )
  //    )
  //    .map(response =>
  //      memberResponseToGatewayEventResponse(
  //        MemberEventResponse.of(response)
  //      ).asInstanceOf[MemberActivated]
  //    )
//
  //override def inactivateMember(
  //    in: InactivateMember
  //): Future[MemberInactivated] =
  //  memberClient
  //    .inactivateMember(
  //      com.improving.app.member.domain.InactivateMember(
  //        in.memberId.map(id => com.improving.app.common.domain.MemberId(id.id)),
  //        in.actingMember.map(id => com.improving.app.common.domain.MemberId(id.id))
  //      )
  //    )
  //    .map(response =>
  //      memberResponseToGatewayEventResponse(
  //        MemberEventResponse.of(response)
  //      ).asInstanceOf[MemberInactivated]
  //    )
//
  //override def suspendMember(in: SuspendMember): Future[MemberSuspended] =
  //  memberClient
  //    .suspendMember(
  //      com.improving.app.member.domain.SuspendMember(
  //        in.memberId.map(id => com.improving.app.common.domain.MemberId(id.id)),
  //        in.actingMember.map(id => com.improving.app.common.domain.MemberId(id.id))
  //      )
  //    )
  //    .map(response =>
  //      memberResponseToGatewayEventResponse(
  //        MemberEventResponse.of(response)
  //      ).asInstanceOf[MemberSuspended]
  //    )
//
  //override def terminateMember(in: TerminateMember): Future[MemberTerminated] =
  //  memberClient
  //    .terminateMember(
  //      com.improving.app.member.domain.TerminateMember(
  //        in.memberId.map(id => com.improving.app.common.domain.MemberId(id.id)),
  //        in.actingMember.map(id => com.improving.app.common.domain.MemberId(id.id))
  //      )
  //    )
  //    .map(response =>
  //      memberResponseToGatewayEventResponse(
  //        MemberEventResponse.of(response)
  //      ).asInstanceOf[MemberTerminated]
  //    )
//
  //override def updateMemberInfo(
  //    in: UpdateMemberInfo
  //): Future[MemberInfoUpdated] =
  //  memberClient
  //    .updateMemberInfo(
  //      com.improving.app.member.domain.UpdateMemberInfo(
  //        in.memberId.map(id => com.improving.app.common.domain.MemberId(id.id)),
  //        in.memberInfo.map(gatewayMemberInfoToMemberInfo),
  //        in.actingMember.map(id => com.improving.app.common.domain.MemberId(id.id))
  //      )
  //    )
  //    .map(response =>
  //      memberResponseToGatewayEventResponse(
  //        MemberEventResponse.of(response)
  //      ).asInstanceOf[MemberInfoUpdated]
  //    )
//
  //override def getMemberInfo(in: domain.GetMemberInfo): Future[domain.MemberData] =
  //  memberClient
  //    .getMemberInfo(
  //      com.improving.app.member.domain.GetMemberInfo(
  //        in.memberId.map(id => com.improving.app.common.domain.MemberId(id.id))
  //      )
  //    )
  //    .map(response => memberResponseToGatewayEventResponse(response).asInstanceOf[domain.MemberData])
//
}
