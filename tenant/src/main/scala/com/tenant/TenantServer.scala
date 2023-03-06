package com.tenant

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TenantServer {
  def main(args: Array[String]): Unit = {
    println("running main of TenantServer")
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem[TestActor.TestCommand](TestActor(), "TenantServer", conf)
    new TenantServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class TenantServer(system: ActorSystem[TestActor.TestCommand]) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys = system
    implicit val ec: ExecutionContext = system.executionContext

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      TenantServiceHandler.withServerReflection(new TenantServiceImpl(system))

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = "0.0.0.0", port = 8080)
      //      .enableHttps(serverHttpContext)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 30.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        println("gRPC server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        println("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    bound
  }
}
