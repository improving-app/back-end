package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.common.domain.{EventId, OrganizationId}
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.event.{
  AllEventIds => GatewayAllEventIds,
  CancelEvent => GatewayCancelEvent,
  CreateEvent => GatewayCreateEvent,
  DelayEvent => GatewayDelayEvent,
  EndEvent => GatewayEndEvent,
  EventCancelled,
  EventCreated,
  EventData,
  EventDelayed,
  EventEnded,
  EventRescheduled,
  EventScheduled,
  EventStarted,
  EventState,
  RescheduleEvent => GatewayRescheduleEvent,
  ScheduleEvent => GatewayScheduleEvent,
  StartEvent => GatewayStartEvent
}
import com.improving.app.gateway.domain.eventUtil._
import com.improving.app.event.api.EventServiceClient
import com.improving.app.event.domain.{
  CancelEvent,
  CreateEvent,
  DelayEvent,
  EndEvent,
  GetEventData,
  RescheduleEvent,
  ScheduleEvent,
  StartEvent
}
import com.typesafe.scalalogging.StrictLogging

import java.util.UUID
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

  def getEventData(id: String): Future[EventData] = {
    try {
      UUID.fromString(id)
    } catch {
      case e: Exception => Future.failed(e)
    }

    eventClient
      .getEventData(GetEventData(Some(EventId(id))))
      .map(data =>
        EventData(
          Some(EventId(id)),
          data.eventInfo.flatMap(_.toGatewayInfoOrEditable),
          data.eventMetaInfo.map(_.toGatewayEventMeta)
        )
      )
  }

  def getAllIds: Future[GatewayAllEventIds] =
    eventClient.getAllIds(com.google.protobuf.empty.Empty()).map { response =>
      GatewayAllEventIds(response.allEventIds)
    }

  def filterEventDataByStatus(eventData: Seq[EventData], status: Option[EventState]): Seq[EventData] =
    status match {
      case Some(state) => eventData.filter(_.eventMetaInfo.map(_.currentState).contains(state))
      case None        => eventData
    }

  def filterEventDataByOrgId(eventData: Seq[EventData], orgId: Option[OrganizationId]): Seq[EventData] =
    orgId match {
      case Some(organizationId) =>
        eventData
          .filter(
            _.eventInfo.exists(info =>
              if (info.value.isInfo) info.getInfo.sponsoringOrg.contains(organizationId)
              else info.getEditable.sponsoringOrg.contains(organizationId)
            )
          )
      case None => eventData
    }

  def getAllIdsAndGetData(
      status: Option[EventState] = None,
      orgId: Option[OrganizationId] = None
  ): Future[Seq[EventData]] =
    eventClient
      .getAllIds(com.google.protobuf.empty.Empty())
      .map { response =>
        GatewayAllEventIds(response.allEventIds)
      }
      .map(_.allEventIds.map(id => getEventData(id.id)))
      .flatMap(allIdsFut =>
        Future
          .sequence(allIdsFut)
          .map(data => filterEventDataByOrgId(filterEventDataByStatus(data, status), orgId))
      )

  def rescheduleEvent(in: GatewayRescheduleEvent): Future[EventRescheduled] =
    eventClient
      .rescheduleEvent(RescheduleEvent(in.eventId, in.start, in.end, in.onBehalfOf))
      .map(response =>
        EventRescheduled(response.eventId, response.info.map(_.toGatewayInfo), response.meta.map(_.toGatewayEventMeta))
      )

  def delayEvent(in: GatewayDelayEvent): Future[EventDelayed] =
    eventClient
      .delayEvent(DelayEvent(in.eventId, in.reason, in.expectedDuration, in.onBehalfOf))
      .map(response =>
        EventDelayed(response.eventId, response.info.map(_.toGatewayInfo), response.meta.map(_.toGatewayEventMeta))
      )

  def startEvent(in: GatewayStartEvent): Future[EventStarted] =
    eventClient
      .startEvent(StartEvent(in.eventId, in.onBehalfOf))
      .map(response =>
        EventStarted(response.eventId, response.info.map(_.toGatewayInfo), response.meta.map(_.toGatewayEventMeta))
      )

  def endEvent(in: GatewayEndEvent): Future[EventEnded] =
    eventClient
      .endEvent(EndEvent(in.eventId, in.onBehalfOf))
      .map(response => EventEnded(response.eventId, response.meta.map(_.toGatewayEventMeta)))
}
