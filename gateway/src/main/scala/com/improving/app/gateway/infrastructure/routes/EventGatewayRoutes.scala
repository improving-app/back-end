package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.common.domain.OrganizationId
import com.improving.app.common.domain.util.GeneratedMessageUtil
import com.improving.app.gateway.api.handlers.EventGatewayHandler
import com.improving.app.gateway.domain.event.{
  CancelEvent => GatewayCancelEvent,
  CreateEvent => GatewayCreateEvent,
  EventData,
  EventState,
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
                complete(eventScheduled.print)
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
                complete(eventCancelled.print)
              }
            }
          }
        } ~ pathPrefix("allIds") {
          get {
            onSuccess(
              handler.getAllIds
            ) { allIds =>
              complete(allIds.print)
            }
          }
        } ~ pathPrefix("allData") {
          pathPrefix("status") {
            pathPrefix(Segment) { status =>
              pathPrefix("forOrg") {
                pathPrefix(Segment) { orgId =>
                  get {
                    onSuccess(
                      handler.getAllIdsAndGetData(EventState.fromName(status), Some(OrganizationId(orgId)))
                    ) { eventDataFut =>
                      complete(
                        eventDataFut.map(_.print)
                      )
                    }
                  }
                }
              } ~ get {
                onSuccess(
                  handler.getAllIdsAndGetData(EventState.fromName(status))
                ) { eventDataFut =>
                  complete(
                    eventDataFut.map(_.print)
                  )
                }
              }
            }
          } ~ get {
            onSuccess(
              handler.getAllIdsAndGetData()
            ) { eventDataFut =>
              complete(eventDataFut.map(_.print))
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
              complete(eventData.print)
            }
          }
        } ~ post {
          entity(Directives.as[String]) { data =>
            logger.info(data)
            onSuccess(
              handler
                .createEvent(
                  fromJsonString[GatewayCreateEvent](data)
                )
            ) { eventCreated =>
              complete(eventCreated.print)
            }
          }
        }
      }
    }
}
