package com.nike.inventory

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.actor.typed.scaladsl.adapter._
import akka.discovery.Discovery
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.nike.inventory.api.ProductAvailabilityServiceHandler
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}

object ProductAvailabilityServer {
  def main(args: Array[String]): Unit = {
    val system = sys.env.get("RUN_LOCAL") match {
      case Some(_) =>
        // Local testing with docker compose
        val config = ConfigFactory.load("local-application.conf").resolve()
        ActorSystem(config.getString("clustering.cluster.name"), config)
      case None =>
        // Kubernetes deployment
        val config = ConfigFactory.defaultApplication()
        val system = ActorSystem("nike-inventory", config)
        AkkaManagement(system).start()
        ClusterBootstrap(system).start()
        Discovery(system).loadServiceDiscovery("kubernetes-api")
        system
    }

    val sharding = ClusterSharding(system.toTyped)

    sharding.init(Entity(typeKey = ProductAvailability.TypeKey) { entityContext =>
      ProductAvailability(entityContext.entityId)
    })

    new ProductAvailabilityServer(system).run()
  }
}

class ProductAvailabilityServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      ProductAvailabilityServiceHandler.withServerReflection(new ProductAvailabilityServiceImpl(system.toTyped))

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().newServerAt("127.0.0.1", 8080).bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}
