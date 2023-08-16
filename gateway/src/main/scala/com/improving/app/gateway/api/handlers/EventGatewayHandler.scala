package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.event.{
  AllEventIds => GatewayAllEventIds,
  CancelEvent => GatewayCancelEvent,
  CreateEvent => GatewayCreateEvent,
  EventCancelled,
  EventCreated,
  EventScheduled,
  ScheduleEvent => GatewayScheduleEvent
}
import com.improving.app.gateway.domain.eventUtil._
import com.improving.app.event.api.EventServiceClient
import com.improving.app.event.domain.{CancelEvent, CreateEvent, ScheduleEvent}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class EventGatewayHandler(grpcClientSettingsOpt: Option[GrpcClientSettings] = None)(implicit
    val system: ActorSystem[_]
) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = getHostAndPortForService("event-service")

  private val eventClient: EventServiceClient = EventServiceClient(
    grpcClientSettingsOpt.getOrElse(
      GrpcClientSettings
        .connectToServiceAt(clientHost, clientPort)
        .withTls(false)
    )
  )

  def createEvent(in: GatewayCreateEvent): Future[EventCreated] =
    eventClient
      .createEvent(
        CreateEvent(
          in.eventId,
          in.info.map(_.toEditableInfo),
          in.onBehalfOf
        )
      )
      .map { response =>
        EventCreated(
          response.eventId,
          response.info.map(_.toGatewayEditableInfo),
          response.meta.map(_.toGatewayEventMeta)
        )
      }

  def scheduleEvent(in: GatewayScheduleEvent): Future[EventScheduled] =
    eventClient
      .scheduleEvent(
        ScheduleEvent(
          in.eventId,
          None,
          in.onBehalfOf
        )
      )
      .map { response =>
        EventScheduled(
          response.eventId,
          response.info.map(_.toGatewayEditableInfo.toInfo),
          response.meta.map(_.toGatewayEventMeta)
        )
      }

  def cancelEvent(in: GatewayCancelEvent): Future[EventCancelled] =
    eventClient
      .cancelEvent(
        CancelEvent(
          in.eventId,
          "Cancellation reason",
          in.onBehalfOf
        )
      )
      .map { response =>
        EventCancelled(
          response.eventId,
          response.meta.map(_.toGatewayEventMeta)
        )
      }

  def getAllIds: Future[GatewayAllEventIds] =
    eventClient.getAllIds(com.google.protobuf.empty.Empty()).map(response => GatewayAllEventIds(response.allEventIds))
}
