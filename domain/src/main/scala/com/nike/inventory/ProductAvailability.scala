package com.nike.inventory

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.PersistenceId
import org.slf4j.Logger
import ProductAvailabilityCommands._
import ProductAvailabilityEvents._

object ProductAvailability {
  sealed trait State extends CborSerializable {
    def sku: String
  }

  final case class EmptyState(sku: String) extends State

  final case class ActiveState(
    sku: String,
    quantity: Int = 0) extends State {

    def withItemAdded(event: ItemAdded): ActiveState =
      copy(quantity = quantity + 1)

    def withItemRemoved(event: ItemRemoved): ActiveState =
      copy(quantity = quantity - 1)
  }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ProductAvailability")

  def apply(sku: String, tag: String): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(sku),
        emptyState = EmptyState(sku),
        commandHandler = commandHandler(context.log),
        eventHandler = eventHandler(sku, context.log)).withTagger(_ => Set(tag))
    }

  private def commandHandler(log: Logger): (State, Command) => Effect[Event, State] = { (state, command) =>
    state match {
      case EmptyState(sku) =>
        command match {
          case addItem @ AddItemCommand(sku) =>
            log.info(s"AddItem $addItem")
            Effect.persist(ItemAdded(sku, 1))
          case command: GetProductAvailabilityCommand =>
            log.info(s"GetProductAvailabilityCommand ${command.sku}")
            command.replyTo ! ProductAvailabilityReply(sku, 0)
            Effect.none
          case command: Command =>
            log.error(s"unexpected command [$command] in state [$state]")
            Effect.none
        }
      case ActiveState(sku, quantity) =>
        command match {
          case addItem @ AddItemCommand(sku) =>
            log.info(s"AddItem $addItem")
            Effect.persist(ItemAdded(sku, quantity + 1))
          case removeItem @ RemoveItemCommand(sku) =>
            log.info(s"RemoveItem $removeItem")
            Effect.persist(ItemRemoved(sku, quantity - 1))
          case command: GetProductAvailabilityCommand =>
            command.replyTo ! ProductAvailabilityReply(sku, quantity)
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
