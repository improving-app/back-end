package com.inventory

import com.inventory.commands.v1.{Ack, AddItemCommand, GetProductAvailabilityCommand, ProductAvailabilityReply, RemoveItemCommand}

import scala.concurrent.Future

/**
 * Abstracts sharding away from direct access, useful for testing when cluster sharding is unavailable.
 */
trait ProductAvailabilityInterface {
  def sendAddItemCommand(command: AddItemCommand): Future[Ack]
  def sendRemoveItemCommand(command: RemoveItemCommand): Future[Ack]
  def sendGetProductAvailabilityCommand(command: GetProductAvailabilityCommand): Future[ProductAvailabilityReply]
}
