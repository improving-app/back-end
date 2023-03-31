package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives.{complete, path}
import akka.util.Timeout
import com.improving.app.gateway.domain.MemberMessages._
import com.improving.app.gateway.domain.common.util.{gatewayMemberInfoToMemberInfo, getHostAndPortForService, memberResponseToGatewayEventResponse}
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.member.domain.MemberEventResponse

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.language.postfixOps

class MemberGatewayHandler(implicit val system: ActorSystem[_]) {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  private val (clientHost, clientPort) = getHostAndPortForService("member")

  private val memberClient = MemberServiceClient(
    GrpcClientSettings
      .connectToServiceAt(clientHost, clientPort)
      .withTls(false)
  )

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "MemberGateway")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    val route =
      path("hello") {
        Directives.get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

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
