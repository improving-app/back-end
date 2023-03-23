package com.improving.app.tenant

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.tenant.api.TenantServiceHandler
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * This is the running application for the Tenant project. This file represents the typical pattern for a gRPC server
 * and the parts that only vary across projects is the projectName, port, Actor to start off with, and the names of the
 * ServiceHandlers/ServiceImpl.
 */
object Main extends App with StrictLogging {
  val projectName = "improving-app-tenant"
  val port = 8080

  val conf = ConfigFactory
    .load("application.conf")
    .withFallback(ConfigFactory.defaultApplication())
  implicit val system = ActorSystem[Nothing](Behaviors.empty, projectName, conf)

  // ActorSystem threads will keep the app alive until `system.terminate()` is called

  // Akka boot up code
  implicit val ec: ExecutionContext = system.executionContext

  // Create service handlers
  val service: HttpRequest => Future[HttpResponse] =
    TenantServiceHandler.withServerReflection(new TenantServiceImpl(system))

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
