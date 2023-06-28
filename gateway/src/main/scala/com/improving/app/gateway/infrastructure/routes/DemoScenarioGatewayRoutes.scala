package com.improving.app.gateway.infrastructure.routes

import akka.grpc.GrpcServiceException
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.improving.app.gateway.api.handlers.TenantGatewayHandler
import com.improving.app.gateway.domain.tenant.{EstablishTenant => GatewayEstablishTenant}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.Json

trait DemoScenarioGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def demoScenarioRoutes(handler: TenantGatewayHandler): Route = logRequestResult("DemoScenarioGateway") {
    pathPrefix("demo") {
      pathPrefix("startScenario") {
        post {
          entity(as[String]) { establishTenant =>
            onSuccess(
              handler
                .establishTenant(
                  GatewayEstablishTenant.fromAscii(establishTenant)
                )
            ) { tenantEstablished =>
              complete(tenantEstablished.toProtoString)
            }
          }
        }
      }
    }
  }
}
