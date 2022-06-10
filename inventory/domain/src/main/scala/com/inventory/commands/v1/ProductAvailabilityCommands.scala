package com.inventory.commands.v1

import akka.actor.typed.ActorRef
import com.inventory.CborSerializable

sealed trait Command extends CborSerializable {
  def entityId: String
}

final case class AddItemCommand(
  override val entityId: String,
  style: String,
  color: String,
  size: String,
  replyTo: Option[ActorRef[Ack]]) extends Command

final case class RemoveItemCommand(
  override val entityId: String,
  style: String, color: String,
  size: String,
  replyTo: Option[ActorRef[Ack]]) extends Command

final case class GetProductAvailabilityCommand(
  override val entityId: String,
  style: String,
  color: String,
  size: String,
  replyTo: Option[ActorRef[ProductAvailabilityReply]]) extends Command

final case class ProductAvailabilityReply(
  style: String,
  color: String,
  size: String,
  quantity: Int) extends CborSerializable

final case class Ack(message: String) extends CborSerializable
