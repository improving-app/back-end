package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.common.domain.EventId
import com.improving.app.gateway.api.handlers.EventGatewayHandler
import com.improving.app.gateway.domain.event.{
  CancelEvent => GatewayCancelEvent,
  CreateEvent => GatewayCreateEvent,
  ScheduleEvent => GatewayScheduleEvent
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString

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
