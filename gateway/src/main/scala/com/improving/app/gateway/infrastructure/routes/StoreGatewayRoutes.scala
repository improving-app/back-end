package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.improving.app.gateway.api.handlers.StoreGatewayHandler
import com.improving.app.gateway.domain.store.{
  CloseStore => GatewayCloseStore,
  CreateStore => GatewayCreateStore,
  DeleteStore => GatewayDeleteStore,
  MakeStoreReady => GatewayMakeStoreReady,
  OpenStore => GatewayOpenStore
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString

trait StoreGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def storeRoutes(handler: StoreGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("StoreGateway") {
      pathPrefix("store") {
        pathPrefix("ready") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .readyStore(
                    fromJsonString[GatewayMakeStoreReady](data)
                  )
              ) { storeReady =>
                complete(JsonFormat.toJsonString(storeReady))
              }
            }
          }
        } ~ pathPrefix("open") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .openStore(
                    fromJsonString[GatewayOpenStore](data)
                  )
              ) { storeOpened =>
                complete(JsonFormat.toJsonString(storeOpened))
              }
            }
          }
        } ~ pathPrefix("close") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .closeStore(
                    fromJsonString[GatewayCloseStore](data)
                  )
              ) { storeClosed =>
                complete(JsonFormat.toJsonString(storeClosed))
              }
            }
          }
        } ~ pathPrefix("delete") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .deleteStore(
                    fromJsonString[GatewayDeleteStore](data)
                  )
              ) { storeDeleted =>
                complete(JsonFormat.toJsonString(storeDeleted))
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
                .createStore(
                  fromJsonString[GatewayCreateStore](data)
                )
            ) { storeCreated =>
              complete(JsonFormat.toJsonString(storeCreated))
            }
          }
        }
      }
    }
}
