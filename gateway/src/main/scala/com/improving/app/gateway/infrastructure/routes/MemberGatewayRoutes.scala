package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.Route
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.improving.app.gateway.domain.{RegisterMember => GatewayRegisterMember}

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

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
