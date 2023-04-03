package com.improving.app.gateway.infrastructure.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.Route
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.MemberMessages.RegisterMember
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec

import scala.concurrent.ExecutionContext

trait MemberGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {
  implicit val system: ActorSystem[_]
  implicit def executor: ExecutionContext

  private val handler = new MemberGatewayHandler()

  val routes: Route = {
    logRequestResult("MemberGateway") {
      pathPrefix("/member") {
        (post & entity(as[RegisterMember])) { registerMember =>
          complete {
            handler.registerMember(registerMember).map[ToResponseMarshallable]
          }
        }
      }
    }
  }
}
