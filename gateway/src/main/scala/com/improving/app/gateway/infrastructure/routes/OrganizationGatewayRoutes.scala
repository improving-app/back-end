package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.gateway.api.handlers.OrganizationGatewayHandler
import com.improving.app.gateway.domain.organization.{ActivateOrganization => GatewayActivateOrganization, EstablishOrganization => GatewayEstablishOrganization, TerminateOrganization => GatewayTerminateOrganization}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString

trait OrganizationGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def organizationRoutes(handler: OrganizationGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("OrganizationGateway") {
      pathPrefix("organization") {
        pathPrefix("activate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .activateOrganization(
                    fromJsonString[GatewayActivateOrganization](data)
                  )
              ) { orgActivated =>
                complete(JsonFormat.toJsonString(orgActivated))
              }
            }
          }
        } ~ pathPrefix("terminate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .terminateOrganization(
                    fromJsonString[GatewayTerminateOrganization](data)
                  )
              ) { orgTerminated =>
                complete(JsonFormat.toJsonString(orgTerminated))
              }
            }
          }
        } ~ post {
          entity(Directives.as[String]) { data =>
            onSuccess(
              handler
                .establishOrganization(
                  fromJsonString[GatewayEstablishOrganization](data)
                )
            ) { orgEstablished =>
              complete(JsonFormat.toJsonString(orgEstablished))
            }
          }
        }
      }
    }
}
