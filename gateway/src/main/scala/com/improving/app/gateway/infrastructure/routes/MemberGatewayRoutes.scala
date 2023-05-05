package com.improving.app.gateway.infrastructure.routes

import akka.grpc.GrpcServiceException
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, ClientError, InternalServerError}
import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.{RegisterMember => GatewayRegisterMember}
import io.circe.Json

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler { case e: GrpcServiceException =>
      complete(
        HttpResponse(
          BadRequest,
          entity = HttpEntity(ContentTypes.`application/json`, Json.fromString(e.getMessage).toString())
        )
      )
    }

  def routes(handler: MemberGatewayHandler): Route = logRequestResult("MemberGateway") {
    pathPrefix("member") {
      post {
        entity(as[String]) { registerMember =>
          onSuccess(
            handler
              .registerMember(
                GatewayRegisterMember.fromAscii(registerMember)
              )
          ) { memberRegistered =>
            complete(memberRegistered.toProtoString)
          }
        }
      }
    }
  }
}
