package com.improving.app.gateway.infrastructure.routes

import akka.grpc.GrpcServiceException
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.member.{RegisterMember => GatewayRegisterMember}
import io.circe.Json

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def memberRoutes(handler: MemberGatewayHandler): Route = logRequestResult("MemberGateway") {
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
