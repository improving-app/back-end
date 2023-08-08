package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.improving.app.common.domain.MemberId
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.member.{
  ActivateMember => GatewayActivateMember,
  GetMemberInfo => GatewayGetMemberInfo,
  RegisterMember => GatewayRegisterMember,
  TerminateMember => GatewayTerminateMember
}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormat.fromJsonString

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def memberRoutes(handler: MemberGatewayHandler)(implicit exceptionHandler: ExceptionHandler): Route =
    logRequestResult("MemberGateway") {
      pathPrefix("member") {
        pathPrefix("activate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .activateMember(
                    fromJsonString[GatewayActivateMember](data)
                  )
              ) { memberActivated =>
                complete(JsonFormat.toJsonString(memberActivated))
              }
            }
          }
        } ~ pathPrefix("terminate") {
          post {
            entity(Directives.as[String]) { data =>
              onSuccess(
                handler
                  .terminateMember(
                    fromJsonString[GatewayTerminateMember](data)
                  )
              ) { memberTerminated =>
                complete(JsonFormat.toJsonString(memberTerminated))
              }
            }
          }
        } ~ pathPrefix(Segment) { memberId =>
          get {
            onSuccess(
              handler
                .getMemberData(
                  memberId
                )
            ) { memberInfo =>
              complete(JsonFormat.toJsonString(memberInfo))
            }
          }
        } ~ post {
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
