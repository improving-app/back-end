package com.improving.app.gateway.infrastructure

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import com.typesafe.scalalogging.StrictLogging

/**
 * This is the running application for the Gateway of the Improving.App project.
 */
object Main extends App with StrictLogging {

  implicit lazy val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "Gateway-ActorSystem")
  lazy val server: GatewayServerImpl = new GatewayServerImpl()

  server.start()
}
