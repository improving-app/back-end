package com.improving.app.gateway.infrastructure.routes

import akka.grpc.GrpcServiceException
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.member.{RegisterMember => GatewayRegisterMember}
import io.circe.Json
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import org.json4s.{JObject, JValue}
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.{fromJson, fromJsonString}

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def memberRoutes(handler: MemberGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("MemberGateway") {
      pathPrefix("member") {
        post {
          entity(Directives.as[String]) { data =>
            onSuccess(
              handler
                .registerMember(
                  fromJsonString[GatewayRegisterMember](data)
                )
            ) { memberRegistered =>
              complete(JsonFormat.toJsonString(memberRegistered))
            }
          }
        }
      }
    }
}
