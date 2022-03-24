package com.inventory

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.PersistenceId
import org.slf4j.Logger
import com.inventory.commands.v1.{Ack, AddItemCommand, Command, GetProductAvailabilityCommand, ProductAvailabilityReply, RemoveItemCommand}
import com.inventory.events.v1.{ItemAdded, ItemRemoved}

import scala.concurrent.duration._

object ProductAvailability {
  private final val PersistenceIdDelimiter = "_"

  def toPersistenceId(style: String, color: String, size: String): String =
    s"$style$PersistenceIdDelimiter$color$PersistenceIdDelimiter$size"

  sealed trait State extends CborSerializable
  final case class EmptyState() extends State

  final case class ActiveState(
    style: String,
    color: String,
    size: String,
    quantity: Int = 0) extends State {

    def withItemAdded(): ActiveState =
      copy(quantity = quantity + 1)

    def withItemRemoved(): ActiveState = {
      copy(quantity = quantity - 1)
    }
  }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ProductAvailability")

  def apply(entityId: String, tag: String): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.debug(s"starting entity:$entityId")
      EventSourcedBehavior[Command, Any, State](
        persistenceId = PersistenceId.ofUniqueId(entityId),
        emptyState = EmptyState(),
        commandHandler = commandHandler(context.log),
        eventHandler = eventHandler())
          .withTagger(_ => Set(tag))
          .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }
  }

  private def commandHandler(log: Logger): (State, Command) => Effect[Any, State] = { (state, command) =>
    log.debug(s"received command:$command")
    state match {
      case EmptyState() =>
        command match {
          case AddItemCommand(entityId, style, color, size, replyTo) =>
            replyTo.map(_ ! Ack(entityId))
            Effect.persist(ItemAdded(entityId, style, color, size, 1))
          case GetProductAvailabilityCommand(_, _, _, _, replyTo) =>
            replyTo.foreach(_ ! ProductAvailabilityReply("", "", "", 0))
            Effect.none
          case command: Command =>
            log.error(s"unexpected command [$command] in state [$state]")
            Effect.none
        }
      case ActiveState(style, color, size, quantity) =>
        command match {
          case AddItemCommand(entityId, style, color, size, replyTo) =>
            replyTo.foreach(_ ! Ack(entityId))
            Effect.persist(ItemAdded(entityId, style, color, size, quantity + 1))
          case RemoveItemCommand(entityId, style, color, size, replyTo) =>
            replyTo.foreach(_ ! Ack(entityId))

            if (quantity > 0)
              Effect.persist(ItemRemoved(entityId, style, color, size, quantity - 1))
            else
              Effect.none
          case GetProductAvailabilityCommand(_, _, _, _, replyTo) =>
            replyTo.foreach(_ ! ProductAvailabilityReply(style, color, size, quantity))
            Effect.none
        }
    }
  }

  private def eventHandler(): (State, Any) => State = { (state, event) =>
    state match {
      case EmptyState() =>
        event match {
          case ItemAdded(_, style, color, size, onHandQuantity) =>
            ActiveState(style, color, size, onHandQuantity)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case active: ActiveState =>
        event match {
          case ItemAdded(_, _, _, _, _) =>
            active.withItemAdded()
          case ItemRemoved(_, _, _, _, _) =>
            active.withItemRemoved()
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
    }
  }
}
