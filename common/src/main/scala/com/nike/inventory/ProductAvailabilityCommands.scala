package com.nike.inventory

import akka.actor.typed.ActorRef

object ProductAvailabilityCommands {
  sealed trait Command {
    def sku: String
  }

  final case class AddItemCommand(sku: String) extends Command
  final case class RemoveItemCommand(sku: String) extends Command
  final case class GetProductAvailabilityCommand(sku: String, replyTo: ActorRef[Reply]) extends Command

  sealed trait Reply
  final case class ProductAvailabilityReply(sku: String, quantity: Int) extends Reply
}
