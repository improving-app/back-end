package com.nike.inventory

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.{actor => classic}
import com.nike.inventory.api.ProductAvailabilityServiceHandler

import scala.concurrent.Future

/**
 * Contains the bootstrapping of this entire app/cluster/persistence/discover/management.
 */
object ProductAvailabilityApp extends App {

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: classic.ActorSystem = context.system.toClassic
    implicit val ec = context.system.executionContext

    val cluster = Cluster(context.system)
    context.log.info("Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      ProductAvailabilityServiceHandler.withServerReflection(new ProductAvailabilityServiceImpl(context.system))

    // Bind service handler servers to localhost:8080
    val binding = Http().newServerAt("0.0.0.0", 8080).bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    AkkaManagement.get(classicSystem).start()
    ClusterBootstrap.get(classicSystem).start()
    val sharding = ClusterSharding(context.system)

    SchemaUtils.createIfNotExists()

    sharding.init(Entity(typeKey = ProductAvailability.TypeKey) { entityContext =>
      ProductAvailability(entityContext.entityId)
    })

    Behaviors.empty
  }, "nike-inventory")
}
