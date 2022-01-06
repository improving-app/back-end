package com.nike.inventory

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.GetShardRegionState
import akka.cluster.sharding.ShardRegion.CurrentShardRegionState
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.nike.inventory.api.{AddItemRequest, GetAvailabilityRequest, GetShardStatsRequest, GetVersionRequest, ProductAvailabilityResponse, RemoveItemRequest, ShardStats, Version}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * GRPC service implementation.
 */
class ProductAvailabilityServiceImpl(system: ActorSystem[_], appVersion: String)
  extends api.ProductAvailabilityService {

  import system.executionContext
  import ProductAvailability._

  private val sharding = ClusterSharding(system)
  private implicit val askTimeout: Timeout = Timeout(5.seconds)

  override def getAvailability(in: GetAvailabilityRequest): Future[ProductAvailabilityResponse] = {
    system.log.debug(s"GetProductAvailabilityCommand $in")
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    system.log.debug(s"GetProductAvailabilityCommand $entityRef")
    val res = entityRef.ask(ref => ProductAvailability.GetProductAvailabilityCommand(in.sku, ref)).mapTo[ProductAvailabilityReply]
    system.log.debug(s"GetProductAvailabilityCommand $res")
    res.map(reply => ProductAvailabilityResponse(reply.sku, reply.quantity))
  }

  override def addItem(in: AddItemRequest): Future[Empty] = {
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    entityRef ! AddItemCommand(in.sku)
    Future(Empty.defaultInstance)
  }

  override def removeItem(in: RemoveItemRequest): Future[Empty] = {
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    entityRef ! RemoveItemCommand(in.sku)
    Future(Empty.defaultInstance)
  }

  override def getShardStats(in: GetShardStatsRequest): Future[ShardStats] = {
    implicit val scheduler = system.scheduler

    val result: Future[CurrentShardRegionState ] = ClusterSharding(system).shardState.ask(
      ref => GetShardRegionState(ProductAvailability.TypeKey, ref)
    )

    result.map ( res =>
      ShardStats(res.shards.mkString)
    )
  }

  override def getVersion(in: GetVersionRequest): Future[Version] =
    Future.successful(Version(appVersion))
}
