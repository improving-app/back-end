package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.MemberMessages.{MemberCommand, MemberEventResponse, RegisterMember}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.syntax._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import cats.implicits.toFunctorOps

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  implicit val decodeMemberCommand: Decoder[MemberCommand] =
    List[Decoder[MemberCommand]](
      Decoder[RegisterMember].widen
    ).reduceLeft(_ or _)

  implicit val encodeMemberResponse: Encoder[MemberEventResponse] = Encoder.instance { _.asJson }

  implicit val handler: MemberGatewayHandler

  val config: Config

  val routes: Route = logRequestResult("MemberGateway") {
    pathPrefix("member") {
      post {
        entity(as[MemberCommand]) { registerMember =>
          onSuccess(
            handler
              .registerMember(registerMember.asInstanceOf[RegisterMember])
          ) { memberRegistered =>
            complete(memberRegistered.asInstanceOf[MemberEventResponse])
          }
        }
      }
    }
  }
}
