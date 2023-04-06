package com.improving.app.gateway.infrastructure

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.http.scaladsl.Http
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.infrastructure.routes.MemberGatewayRoutes
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class GatewayServerImpl(implicit val sys: ActorSystem[_]) extends MemberGatewayRoutes {

  override val config: Config = ConfigFactory
    .load("application.conf")
    .withFallback(ConfigFactory.defaultApplication())

  implicit override val handler: MemberGatewayHandler = new MemberGatewayHandler()

  implicit val dispatcher: ExecutionContextExecutor = sys.dispatchers.lookup(DispatcherSelector.defaultDispatcher())
  def start(): Unit = {
    Http()
      .newServerAt(config.getString("http.interface"), config.getInt("http.port"))
      .bindFlow(routes)
      .onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          println(s"Improving.App HTTP Gateway bound to ${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          println(s"Failed to bind HTTP endpoint for Improving.APP Gateway, terminating system: ${ex.getMessage}")
          sys.terminate()
      }
  }
}
