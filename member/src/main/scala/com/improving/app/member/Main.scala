package com.improving.app.member

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.improving.app.member.api.MemberServiceImpl
import com.typesafe.scalalogging.StrictLogging

import scala.language.postfixOps

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

object Main extends App with StrictLogging {

  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "MemberSystem")

  new MemberServiceImpl()

  logger.info("starting the Member service")

}
