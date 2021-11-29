package com.nike.inventory

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.nike.inventory.api.ProductAvailabilityServiceHandler
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}

object ProductAvailabilityServer {
  def main(args: Array[String]): Unit = {
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)
    new ProductAvailabilityServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class ProductAvailabilityServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    import akka.actor.typed.scaladsl.adapter._
    // Akka boot up code
    implicit val sys: ActorSystem = system
    val typedSystem: akka.actor.typed.ActorSystem[Nothing] = system.toTyped
    implicit val ec: ExecutionContext = sys.dispatcher

    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    val sharding = ClusterSharding(typedSystem)

    sharding.init(Entity(typeKey = ProductAvailability.TypeKey) { entityContext =>
      ProductAvailability(entityContext.entityId)
    })

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      ProductAvailabilityServiceHandler(new ProductAvailabilityServiceImpl(typedSystem))

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().newServerAt("127.0.0.1", 8080).bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}
