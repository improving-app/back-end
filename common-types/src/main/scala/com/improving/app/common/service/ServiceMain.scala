package com.improving.app.common.service

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.config.AppConfig
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * This trait is extended by the gRPC services. This file represents the typical pattern for a gRPC server and the parts
 * that only vary across projects is the projectName, port, and ServiceHandler/ServiceImpl to use.
 */
trait ServiceMain extends App with StrictLogging {
  // projectName is the name of the service, usually in the format of 'improving-app-SOME-SERVICE'
  protected val projectName: String

  // port is where the server should be listening to
  protected val port: Int

  // The service to bind to
  protected def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse]

  /**
   * The function to run for Main
   */
  protected def run(): Unit = {

    val appConfig: AppConfig = new AppConfig(args(0).equals("dynamo"))
    val conf: Config = appConfig.loadConfig
    implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, projectName, conf)

    // ActorSystem threads will keep the app alive until `system.terminate()` is called

    // Akka boot up code
    implicit val ec: ExecutionContext = system.executionContext

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = "0.0.0.0", port = port)
      //      .enableHttps(serverHttpContext)
      .bind(service(system))
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
}
