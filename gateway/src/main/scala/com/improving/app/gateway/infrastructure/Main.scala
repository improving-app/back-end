package com.improving.app.gateway.infrastructure

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import com.typesafe.scalalogging.StrictLogging

/**
 * This is the running application for the Tenant project.
 */
object Main extends App with StrictLogging {

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "MemberGateway-ActorSystem")
  private val server: GatewayServerImpl = new GatewayServerImpl()
  def run(): Unit = server.start()

  run()
}
