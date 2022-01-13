package com.yoppworks.inventory

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import com.yoppworks.inventory.api.ProductAvailabilityServiceHandler

import scala.concurrent.Future

/**
 * Contains the bootstrapping of this entire app/cluster/persistence/discover/management.
 */
object DomainApp extends App {

  val ProductAvailabilityTag = "product-availability"

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val ec = context.system.executionContext
    implicit val system = context.system

    val cluster = Cluster(system)
    context.log.info("Started [" + system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    val sharding = ClusterSharding(system)

    sharding.init(Entity(typeKey = ProductAvailability.TypeKey) { entityContext =>
      ProductAvailability(entityContext.entityId, ProductAvailabilityTag)
    })

    // Get app version for reporting
    val version = system.settings.config.getString("app-version")

    // Create service handler and bind service handler servers to localhost:8080
    val service: HttpRequest => Future[HttpResponse] =
      ProductAvailabilityServiceHandler.withServerReflection(new ProductAvailabilityServiceImpl(system, version))
    val binding = Http()(system.toClassic).newServerAt("0.0.0.0", 8080).bind(service)
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    AkkaManagement.get((system.toClassic)).start()
    ClusterBootstrap.get((system.toClassic)).start()

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    Cluster(system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    Behaviors.empty
  }, "inventory-domain")
}
