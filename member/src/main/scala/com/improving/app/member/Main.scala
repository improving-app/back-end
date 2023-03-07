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
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.{Failure, Success}

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

object Main extends App with StrictLogging {

  private val conf = ConfigFactory
    .parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  implicit val system: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "MemberSystem", conf)
  implicit val ec: ExecutionContext = system.executionContext

  private def run(): Future[Http.ServerBinding] = {

    val service: HttpRequest => Future[HttpResponse] =
      MemberServiceHandler.withServerReflection(new MemberServiceImpl())

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = "127.0.0.1", port = 8080)
      //.enableHttps(serverHttpContext)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

    logger.info(s"Server online at http://localhost:8080/\n")
    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info("gRPC server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        logger.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    bound
  }

  logger.info("starting the Member service")

  run()

}
