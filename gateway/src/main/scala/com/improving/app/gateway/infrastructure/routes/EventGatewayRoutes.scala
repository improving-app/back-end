package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.gateway.api.handlers.EventGatewayHandler
import com.improving.app.gateway.domain.event.{
  CancelEvent => GatewayCancelEvent,
  CreateEvent => GatewayCreateEvent,
  EventData,
  ScheduleEvent => GatewayScheduleEvent
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EventGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def eventRoutes(handler: EventGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("EventGateway") {
      pathPrefix("event") {
        pathPrefix("schedule") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .scheduleEvent(
                    fromJsonString[GatewayScheduleEvent](data)
                  )
              ) { eventScheduled =>
                complete(JsonFormat.toJsonString(eventScheduled))
              }
            }
          }
        } ~ pathPrefix("cancel") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .cancelEvent(
                    fromJsonString[GatewayCancelEvent](data)
                  )
              ) { eventCancelled =>
                complete(JsonFormat.toJsonString(eventCancelled))
              }
            }
          }
        } ~ pathPrefix("allIds") {
          get {
            onSuccess(
              handler.getAllIds
            ) { allIds =>
              complete(JsonFormat.toJsonString(allIds))
            }

          }
        } ~ pathPrefix("allData") {
          get {
            onSuccess(
              handler.getAllIds.map { resp =>
                println("resp: " + resp.toProtoString)
                resp.allEventIds.map(id => handler.getEventData(id.id))
              }
            ) { allIdsFut =>
              complete(Future.sequence(allIdsFut).map(data => data.map(JsonFormat.toJsonString[EventData])))
            }
          }
        } ~ pathPrefix(Segment) { eventId =>
          get {
            onSuccess(
              handler
                .getEventData(
                  eventId
                )
            ) { eventData =>
              complete(JsonFormat.toJsonString(eventData))
            }
          }
        } ~ post {
          entity(Directives.as[String]) { data =>
            onSuccess(
              handler
                .createEvent(
                  fromJsonString[GatewayCreateEvent](data)
                )
            ) { eventCreated =>
              complete(JsonFormat.toJsonString(eventCreated))
            }
          }
        }
        } ~ pathPrefix("allIds") {
          get {
            onSuccess(
              handler.getAllIds
            ) { allIds =>
              complete(JsonFormat.toJsonString(allIds))
            }

          }
        } ~ post {
          entity(Directives.as[String]) { data =>
            onSuccess(
              handler
                .createEvent(
                  fromJsonString[GatewayCreateEvent](data)
                )
            ) { eventCreated =>
              complete(JsonFormat.toJsonString(eventCreated))
            }
          }
        }
      }
    }
}
