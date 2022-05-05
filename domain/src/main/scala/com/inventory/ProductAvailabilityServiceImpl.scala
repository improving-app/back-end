package com.inventory

import akka.actor.typed.ActorSystem
import com.google.protobuf.empty.Empty
import com.inventory.commands.v1.{AddItemCommand, GetProductAvailabilityCommand, RemoveItemCommand}
import com.inventory.api.v1._

import scala.concurrent.Future

/**
 * GRPC service implementation. This handles version 1 protocol, but should be able to handle
 * future versions.
 */
class ProductAvailabilityServiceImpl(interface: ProductAvailabilityInterface, system: ActorSystem[_],
                                     appVersion: String) extends api.v1.ProductAvailabilityService {

  import system.executionContext

  override def getAvailability(in: GetAvailabilityRequest): Future[ProductAvailabilityResponse] = {
    system.log.debug(s"GetAvailability $in")
    interface.sendGetProductAvailabilityCommand(
      GetProductAvailabilityCommand(entityIdFor(in.style, in.color, in.size), in.style, in.color, in.size, None)
    ).map(r => ProductAvailabilityResponse(r.style, r.color, r.size, r.quantity))
  }

  override def addItem(in: AddItemRequest): Future[Empty] = {
    system.log.debug(s"addItem $in")
    interface.sendAddItemCommand(
      AddItemCommand(entityIdFor(in.style, in.color, in.size), in.style, in.color, in.size, None)
    )
    Future(Empty.defaultInstance)
  }

  override def removeItem(in: RemoveItemRequest): Future[Empty] = {
    system.log.debug(s"removeItem $in")
    interface.sendRemoveItemCommand(
      RemoveItemCommand(entityIdFor(in.style, in.color, in.size), in.style, in.color, in.size, None)
    )
    Future(Empty.defaultInstance)
  }

  override def getVersion(in: GetVersionRequest): Future[Version] =
    Future.successful(Version(appVersion))

  private def entityIdFor(style: String, color: String, size: String): String =
    ProductAvailability.toPersistenceId(style, size, color)
}
