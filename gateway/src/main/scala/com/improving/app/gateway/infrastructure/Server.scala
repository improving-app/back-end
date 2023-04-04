package com.improving.app.gateway.infrastructure

import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.http.scaladsl.Http
import com.improving.app.gateway.infrastructure.routes.MemberGatewayRoutes
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

object Server extends App with MemberGatewayRoutes { self =>

  implicit override val system: ActorSystem[Any] = ActorSystem[Any](Behaviors.empty, "Gateway ActorSystem")

  implicit override val executor: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.defaultDispatcher())

  override val config = ConfigFactory.load()

  Http().newServerAt(config.getString("http.interface"), config.getInt("http.port")).bindFlow(routes)

}
// $COVERAGE-ON$
