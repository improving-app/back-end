package com.improving.app.gateway.infrastructure.routes

import akka.grpc.GrpcServiceException
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.gateway.api.handlers.TenantGatewayHandler
import com.improving.app.gateway.domain.tenant.{
  ActivateTenant => GatewayActivateTenant,
  EstablishTenant => GatewayEstablishTenant,
  TerminateTenant => GatewayTerminateTenant
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.Json
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString
import akka.http.scaladsl.server.Directives._

trait TenantGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def tenantRoutes(handler: TenantGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("TenantGateway") {
      pathPrefix("tenant") {
        pathPrefix("activate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .activateTenant(
                    fromJsonString[GatewayActivateTenant](data)
                  )
              ) { tenantActivated =>
                complete(JsonFormat.toJsonString(tenantActivated))
              }
            }
          }
        } ~ pathPrefix("terminate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .terminateTenant(
                    fromJsonString[GatewayTerminateTenant](data)
                  )
              ) { tenantTerminated =>
                complete(JsonFormat.toJsonString(tenantTerminated))
              }
            }
          }
        } ~ pathPrefix("allIds") {
          get {
            onSuccess(
              handler.getAllIds
            ) { allTenantIds =>
              complete(JsonFormat.toJsonString(allTenantIds))
            }
          }
        } ~ post {
          entity(Directives.as[String]) { data =>
            onSuccess(
              handler
                .establishTenant(
                  fromJsonString[GatewayEstablishTenant](data)
                )
            ) { tenantEstablished =>
              complete(JsonFormat.toJsonString(tenantEstablished))
            }
          }
        }
      }
    }
}
