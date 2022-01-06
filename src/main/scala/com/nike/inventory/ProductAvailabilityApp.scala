package com.nike.inventory

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.slick.SlickProjection
import com.nike.inventory.api.ProductAvailabilityServiceHandler
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.concurrent.Future

/**
 * Contains the bootstrapping of this entire app/cluster/persistence/discover/management.
 */
object ProductAvailabilityApp extends App {

  val ProductAvailabilityTag = "product-availability"

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val ec = context.system.executionContext
    implicit val system = context.system

    val cluster = Cluster(system)
    context.log.info("Started [" + system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    // Use for resetting data after breaking refactor.
    //SchemaUtils.dropIfExists(system)
    //SchemaUtils.createIfNotExists(system)
    //

    val sharding = ClusterSharding(system)

    sharding.init(Entity(typeKey = ProductAvailability.TypeKey) { entityContext =>
      ProductAvailability(entityContext.entityId, ProductAvailabilityTag)
    })

    val dbConfig: DatabaseConfig[PostgresProfile] =
      DatabaseConfig.forConfig("akka.projection.slick", system.settings.config)

    SlickProjection.createTablesIfNotExists(dbConfig)

    val sourceProvider =
      EventSourcedProvider
        .eventsByTag[ProductAvailability.Event](context.system, readJournalPluginId = JdbcReadJournal.Identifier,
          tag = ProductAvailabilityTag)

    val projection = SlickProjection.exactlyOnce(
      projectionId = ProjectionId("ProductAvailability", "sku"),
      sourceProvider,
      dbConfig,
      handler = () => new ProductAvailabilityHandler(new LowInventoryRepository(dbConfig)))

    context.spawn(ProjectionBehavior(projection), projection.projectionId.id)

    // Get app version for reporting
    val version = system.settings.config.getString("app-version")

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      ProductAvailabilityServiceHandler.withServerReflection(new ProductAvailabilityServiceImpl(system, version))

    // Bind service handler servers to localhost:8080
    val binding = Http()(system.toClassic).newServerAt("0.0.0.0", 8080).bind(service)

    // report successful binding
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
  }, "nike-inventory")
}
