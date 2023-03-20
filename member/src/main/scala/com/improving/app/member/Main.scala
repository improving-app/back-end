package com.improving.app.member

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.member.api.{MemberServiceHandler, MemberServiceImpl}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main extends App with StrictLogging {
  val projectName = "improving-app-member"
  val port = 8081

  val conf = ConfigFactory
    .load("application.conf")
    .withFallback(ConfigFactory.defaultApplication())
  implicit val system = ActorSystem[Nothing](Behaviors.empty, projectName, conf)

  // ActorSystem threads will keep the app alive until `system.terminate()` is called

  // Akka boot up code
  implicit val ec: ExecutionContext = system.executionContext

  // Create service handlers
  val service: HttpRequest => Future[HttpResponse] =
    MemberServiceHandler.withServerReflection(new MemberServiceImpl())

  val bound: Future[Http.ServerBinding] = Http(system)
    .newServerAt(interface = "0.0.0.0", port = port)
    //      .enableHttps(serverHttpContext)
    .bind(service)
    .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 30.seconds))

  bound.onComplete {
    case Success(binding) =>
      val address = binding.localAddress
      println(s"$projectName gRPC server bound to ${address.getHostString}:${address.getPort}")
    case Failure(ex) =>
      println(s"Failed to bind gRPC endpoint for $projectName, terminating system: ${ex.getMessage}")
      system.terminate()
  }
}
