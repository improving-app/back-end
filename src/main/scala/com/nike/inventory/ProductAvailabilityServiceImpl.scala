package com.nike.inventory

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.nike.inventory.api.{AddItemRequest, GetAvailabilityRequest, ProductAvailabilityResponse, RemoveItemRequest}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * GRPC service implementation.
 */
class ProductAvailabilityServiceImpl(system: ActorSystem[_])
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
    res.map(reply => ProductAvailabilityResponse(reply.sku, reply.metadata, reply.location, reply.quantity))
  }

  override def addItem(in: AddItemRequest): Future[Empty] = {
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    entityRef ! AddItemCommand(in.sku, in.metadata, in.location)
    Future(Empty.defaultInstance)
  }

  override def removeItem(in: RemoveItemRequest): Future[Empty] = {
    val entityRef = sharding.entityRefFor(ProductAvailability.TypeKey, in.sku)
    entityRef ! RemoveItemCommand(in.sku)
    Future(Empty.defaultInstance)
  }
}
