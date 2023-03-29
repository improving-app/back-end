package com.improving.app.gateway.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector, PostStop}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import cats.data.Validated.{Invalid, Valid}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.MemberId
import com.improving.app.gateway.api.MemberGatewayServiceImpl
import com.improving.app.member.domain.MemberStatus
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}
import com.improving.app.gateway.domain.MemberStatus._
import com.improving.app.gateway.domain.util.{
  gatewayMemberInfoToMemberInfo,
  getHostAndPortForService,
  memberResponseToGatewayEventResponse
}
import com.improving.app.member.api.MemberServiceClient
import com.improving.app.tenant.api.MemberGatewayServiceHandler
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object MemberGateway extends StrictLogging {
  def main(args: Array[String]): Unit = {
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val system: ActorSystem[_] = ActorSystem[_]("MemberGateway", conf)
    new MemberGateway().run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class MemberGateway(implicit val system: ActorSystem[_]) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val executor: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.defaultDispatcher())

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      MemberGatewayServiceHandler(new MemberGatewayServiceImpl())

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().newServerAt("127.0.0.1", 8090).bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }(executor)

    binding
  }
}
