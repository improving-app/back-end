package com.improving.app.event.api

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.util.Timeout
import com.google.rpc.Code
import com.improving.app.event.domain.Event.{EventEntityKey, EventEnvelope}
import com.improving.app.event.domain._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class EventServiceImpl(implicit val system: ActorSystem[_]) extends EventService {

  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  // Create a new member
  val sharding: ClusterSharding = ClusterSharding(system)
  ClusterSharding(system).init(
    Entity(EventEntityKey)(entityContext => Event(entityContext.entityTypeKey.name, entityContext.entityId))
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  // Do not use for RegisterEvent

  private def handleResponse[T](
      eventHandler: PartialFunction[StatusReply[EventResponse], T]
  ): PartialFunction[StatusReply[EventResponse], T] = {
    eventHandler.orElse {
      case StatusReply.Success(response) => throw new RuntimeException(s"Unexpected response $response")
      case StatusReply.Error(ex)         => throw ex
    }
  }

  private def handleCommand[T](
      in: EventRequestPB with EventCommand,
      eventHandler: PartialFunction[StatusReply[EventResponse], T]
  ): Future[T] = in.eventId
    .map { id =>
      val eventEntity = sharding.entityRefFor(EventEntityKey, id.id)

      // Register the member
      eventEntity
        .ask[StatusReply[EventResponse]](replyTo => EventEnvelope(in, replyTo))
        .map { handleResponse(eventHandler) }
    }
    .getOrElse(
      Future.failed(
        GrpcServiceException.create(
          Code.INVALID_ARGUMENT,
          "An entity Id was not provided",
          java.util.List.of(in.asMessage)
        )
      )
    )

  private def handleQuery[T](
      in: EventRequestPB with EventQuery,
      eventHandler: PartialFunction[StatusReply[EventResponse], T]
  ): Future[T] = in.eventId
    .map { id =>
      val memberEntity = sharding.entityRefFor(EventEntityKey, id.id)

      // Register the member
      memberEntity
        .ask[StatusReply[EventResponse]](replyTo => EventEnvelope(in, replyTo))
        .map { handleResponse(eventHandler) }
    }
    .getOrElse(
      Future.failed(
        GrpcServiceException.create(
          Code.INVALID_ARGUMENT,
          "An entity Id was not provided",
          java.util.List.of(in.asMessage)
        )
      )
    )

  /**
   * post: "event/{eventId}/"
   */
  override def editEventInfo(in: EditEventInfo): Future[EventInfoEdited] =
    handleCommand(
      in,
      { case StatusReply.Success(EventEventResponse(response @ EventInfoEdited(_, _, _, _), _)) =>
        response
      }
    )

  /**
   * post: "event/{eventId}/create/"
   */
  override def createEvent(in: CreateEvent): Future[EventCreated] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventCreated(_, _, _, _), _)) =>
      response
    }
  )

  /**
   * post: "event/{eventId}/schedule/"
   */
  override def scheduleEvent(in: ScheduleEvent): Future[EventScheduled] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventScheduled(_, _, _, _), _)) =>
      response
    }
  )

  /**
   * post: "event/{eventId}/cancel/"
   */
  override def cancelEvent(in: CancelEvent): Future[EventCancelled] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventCancelled(_, _, _), _)) =>
      response
    }
  )

  /**
   * post: "event/{eventId}/reschedule/"
   */
  override def rescheduleEvent(in: RescheduleEvent): Future[EventRescheduled] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventRescheduled(_, _, _, _), _)) =>
      response
    }
  )

  /**
   * get:"event/{eventId}/delay"
   */
  override def delayEvent(in: DelayEvent): Future[EventDelayed] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventDelayed(_, _, _, _), _)) =>
      response
    }
  )

  /**
   * get:"event/{eventId}/start"
   */
  override def startEvent(in: StartEvent): Future[EventStarted] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventStarted(_, _, _), _)) =>
      response
    }
  )

  /**
   * get:"event/{eventId}/end"
   */
  override def endEvent(in: EndEvent): Future[EventEnded] = handleCommand(
    in,
    { case StatusReply.Success(EventEventResponse(response @ EventEnded(_, _, _), _)) =>
      response
    }
  )
}
