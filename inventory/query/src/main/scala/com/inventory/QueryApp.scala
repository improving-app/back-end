package com.inventory

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.projection.slick.SlickProjection
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Contains the bootstrapping of this entire app/cluster/persistence/discover/management.
 */
object QueryApp extends App {

  val ProductAvailabilityTag = "product-availability"

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val ec = context.system.executionContext
    implicit val system = context.system

    val cluster = Cluster(system)
    context.log.info("Started [" + system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    val dbConfig: DatabaseConfig[PostgresProfile] =
      DatabaseConfig.forConfig("akka.projection.slick", system.settings.config)

    SlickProjection.createTablesIfNotExists(dbConfig) // Not for production

    val repo: LowInventoryRepository = new SlickLowInventoryRepository(dbConfig)
    Await.result(repo.createTable(), 10.seconds) // Not for production

    Http().newServerAt("0.0.0.0", 8080).bind(Routes.routes(new SlickLowInventoryRepository(dbConfig)))
    AkkaManagement.get((system.toClassic)).start()
    ClusterBootstrap.get((system.toClassic)).start()

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    Cluster(system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    new ProductAvailabilityConsumer(repo)

    Behaviors.empty
  }, "nike-inventory-query")
}
