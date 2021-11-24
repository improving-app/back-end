package com.nike.inventory

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.PersistenceId
import org.slf4j.Logger

object ProductAvailability {
  sealed trait Command {
    def sku: String
  }

  final case class AddItem(sku: String, metadata: String, location: String) extends Command
  final case class RemoveItem(sku: String) extends Command

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
      case EmptyState(_) =>
        command match {
          case addItem @ AddItem(sku, metadata, location) =>
            log.debug(s"AddItem $addItem")
            Effect.persist(ItemAdded(sku, metadata, location))
          case command: Command =>
            log.error(s"unexpected command [$command] in state [$state]")
            Effect.none
        }
      case _: ActiveState =>
        command match {
          case addItem @ AddItem(sku, metadata, location) =>
            log.debug(s"AddItem $addItem")
            Effect.persist(ItemAdded(sku, metadata, location))
          case removeItem @ RemoveItem(sku) =>
            log.debug(s"RemoveItem $removeItem")
            Effect.persist(ItemRemoved(sku))
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
