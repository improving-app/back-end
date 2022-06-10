package com.inventory

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.inventory.commands.v1.{Ack, AddItemCommand, GetProductAvailabilityCommand, ProductAvailabilityReply, RemoveItemCommand}

import scala.concurrent.Future
import scala.concurrent.duration._

class ProductAvailabilityShardingInterface(system: ActorSystem[_]) extends ProductAvailabilityInterface {
  implicit val timeout: Timeout = Timeout(5.seconds)

  override def sendAddItemCommand(command: AddItemCommand): Future[Ack] =
    ClusterSharding(system).entityRefFor(ProductAvailability.TypeKey, command.entityId)
      .ask(ref => command.copy(replyTo = Some(ref)))

  override def sendRemoveItemCommand(command: RemoveItemCommand): Future[Ack] =
    ClusterSharding(system).entityRefFor(ProductAvailability.TypeKey, command.entityId)
      .ask(ref => command.copy(replyTo = Some(ref)))

  override def sendGetProductAvailabilityCommand(command: GetProductAvailabilityCommand): Future[ProductAvailabilityReply] =
    ClusterSharding(system).entityRefFor(ProductAvailability.TypeKey, command.entityId)
      .ask(ref => command.copy(replyTo = Some(ref)))
}
