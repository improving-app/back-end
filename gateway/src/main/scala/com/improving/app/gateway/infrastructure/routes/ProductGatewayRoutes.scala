package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.gateway.api.handlers.ProductGatewayHandler
import com.improving.app.gateway.domain.product.{
  CreateProduct => GatewayCreateProduct,
  DeleteProduct => GatewayDeleteProduct,
  ActivateProduct => GatewayActivateProduct
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString

trait ProductGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def productRoutes(handler: ProductGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("ProductGateway") {
      pathPrefix("product") {
        pathPrefix("activate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .activateProduct(
                    fromJsonString[GatewayActivateProduct](data)
                  )
              ) { productActivated =>
                complete(JsonFormat.toJsonString(productActivated))
              }
            }
          }
        } ~ pathPrefix("delete") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .deleteProduct(
                    fromJsonString[GatewayDeleteProduct](data)
                  )
              ) { productDeleted =>
                complete(JsonFormat.toJsonString(productDeleted))
              }
            }
          }
        } ~
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .createProduct(
                    fromJsonString[GatewayCreateProduct](data)
                  )
              ) { productCreated =>
                complete(JsonFormat.toJsonString(productCreated))
              }
            }
          }
      }
    }
}
