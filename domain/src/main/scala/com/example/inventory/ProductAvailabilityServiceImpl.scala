package com.example.inventory

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import ProductAvailabilityCommands._
import com.example.inventory.api.{AddItemRequest, GetAvailabilityRequest, GetVersionRequest, ProductAvailabilityResponse, RemoveItemRequest, Version}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * GRPC service implementation.
 */
class ProductAvailabilityServiceImpl(system: ActorSystem[_], appVersion: String)
  extends api.ProductAvailabilityService {

  import system.executionContext

  private val sharding = ClusterSharding(system)

  override def getAvailability(in: GetAvailabilityRequest): Future[ProductAvailabilityResponse] = {
    implicit val timeout: Timeout = Timeout(5.seconds)
    system.log.debug(s"GetAvailability $in")
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    val res = entityRef.ask(ref => GetProductAvailabilityCommand(in.sku, ref)).mapTo[ProductAvailabilityReply]
    system.log.debug(s"GetProductAvailabilityCommand $res")
    res.map(
      reply =>
        ProductAvailabilityResponse.defaultInstance
          .withSku(reply.sku)
          .withQuantity(reply.quantity)
    )
  }

  override def addItem(in: AddItemRequest): Future[Empty] = {
    system.log.debug(s"addItem $in")
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    entityRef ! AddItemCommand(in.sku)
    Future(Empty.defaultInstance)
  }

  override def removeItem(in: RemoveItemRequest): Future[Empty] = {
    system.log.debug(s"removeItem $in")
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    entityRef ! RemoveItemCommand(in.sku)
    Future(Empty.defaultInstance)
  }

  override def getVersion(in: GetVersionRequest): Future[Version] =
    Future.successful(Version(appVersion))
}
