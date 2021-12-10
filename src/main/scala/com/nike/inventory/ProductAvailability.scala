package com.nike.inventory

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.PersistenceId
import org.slf4j.Logger

object ProductAvailability {
  sealed trait Command {
    def sku: String
  }

  final case class AddItemCommand(sku: String, metadata: String, location: String) extends Command
  final case class RemoveItemCommand(sku: String) extends Command
  final case class GetProductAvailabilityCommand(sku: String, replyTo: ActorRef[Reply]) extends Command

  sealed trait Reply extends CborSerializable
  final case class ProductAvailabilityReply(sku: String, metadata: String, location: String, quantity: Int) extends Reply

  sealed trait Event extends CborSerializable {
    def sku: String
  }

  final case class ItemAdded(sku: String, metadata: String, location: String) extends Event
  final case class ItemRemoved(sku: String) extends Event

  sealed trait State extends CborSerializable {
    def sku: String
  }

  final case class EmptyState(sku: String) extends State

  final case class ActiveState(
    sku: String,
    metadata: String = "",
    location: String = "",
    quantity: Int = 0) extends State {

    def withItemAdded(event: ItemAdded): ActiveState =
      copy(metadata = event.metadata, location = event.location, quantity = quantity + 1)

    def withItemRemoved(event: ItemRemoved): ActiveState =
      copy(quantity = quantity - 1)
  }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ProductAvailability")

  def apply(sku: String): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(sku),
        emptyState = EmptyState(sku),
        commandHandler = commandHandler(context.log),
        eventHandler = eventHandler(sku, context.log))
    }

  private def commandHandler(log: Logger): (State, Command) => Effect[Event, State] = { (state, command) =>
    state match {
      case EmptyState(sku) =>
        command match {
          case addItem @ AddItemCommand(sku, metadata, location) =>
            log.info(s"AddItem $addItem")
            Effect.persist(ItemAdded(sku, metadata, location))
          case command: GetProductAvailabilityCommand =>
            log.info(s"GetProductAvailabilityCommand ${command.sku}")
            command.replyTo ! ProductAvailabilityReply(sku, "", "", 0)
            Effect.none
          case command: Command =>
            log.error(s"unexpected command [$command] in state [$state]")
            Effect.none
        }
      case ActiveState(sku, metadata, location, quantity) =>
        command match {
          case addItem @ AddItemCommand(sku, metadata, location) =>
            log.info(s"AddItem $addItem")
            Effect.persist(ItemAdded(sku, metadata, location))
          case removeItem @ RemoveItemCommand(sku) =>
            log.info(s"RemoveItem $removeItem")
            Effect.persist(ItemRemoved(sku))
          case command: GetProductAvailabilityCommand =>
            command.replyTo ! ProductAvailabilityReply(sku, metadata, location, quantity)
            Effect.none
        }
    }
  }

  private def eventHandler(sku: String, log: Logger): (State, Event) => State = { (state, event) =>
    state match {
      case EmptyState(_) =>
        event match {
          case itemAdded: ItemAdded =>
            log.info(s"ItemAdded $itemAdded")
            ActiveState(sku).withItemAdded(itemAdded)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case active: ActiveState =>
        event match {
          case itemAdded: ItemAdded =>
            log.info(s"ItemAdded $itemAdded")
            active.withItemAdded(itemAdded)
          case itemRemoved: ItemRemoved =>
            log.info(s"ItemRemoved $itemRemoved")
            active.withItemRemoved(itemRemoved)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
    }
  }
}
