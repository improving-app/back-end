package com.improving.app.gateway

import akka.actor.typed
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server.Route

import scala.concurrent.duration.DurationInt
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import cats.implicits.toFunctorOps
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.MemberMessages.{
  MemberCommand,
  MemberData,
  MemberEventResponse,
  MemberRegistered,
  MemberResponse,
  RegisterMember
}
import com.improving.app.gateway.domain.common.util.memberInfoToGatewayMemberInfo
import com.improving.app.gateway.infrastructure.routes.MemberGatewayRoutes
import com.improving.app.member.domain.TestData.baseMemberInfo
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures

import scala.language.postfixOps
import java.util.UUID

class GatewayServerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with BeforeAndAfterEach
    with MemberGatewayRoutes {
  implicit val encodeMemberCommand: Encoder[MemberCommand] = Encoder.instance {
    case response @ RegisterMember(_, _, _) =>
      response.asJson
  }

  implicit val decodeMemberResponse: Decoder[MemberResponse] =
    List[Decoder[MemberResponse]](
      Decoder[MemberRegistered].widen
    ).reduceLeft(_ or _)

  implicit val decodeMemberEventResponse: Decoder[MemberEventResponse] =
    List[Decoder[MemberEventResponse]](
      Decoder[MemberResponse].widen,
      Decoder[MemberData].widen
    ).reduceLeft(_ or _)

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(10.seconds)

  implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped

  override val config: Config = ConfigFactory.load("application.conf")

  implicit override val handler: MemberGatewayHandler = new MemberGatewayHandler()

  implicit val defaultHostInfo: DefaultHostInfo = DefaultHostInfo(Host.apply("localhost"), securedConnection = false)
  "In MemberGateway" when {
    "Sending RegisterMember" should {
      "succeed" in {
        val info = memberInfoToGatewayMemberInfo(baseMemberInfo)

        val memberId = UUID.randomUUID()
        val registeringMember = UUID.randomUUID()

        val command = RegisterMember(memberId, info, registeringMember)

        Post("/member", command.asInstanceOf[MemberCommand].asJson) ~> Route.seal(
          routes
        ) ~> check {
          status shouldEqual StatusCodes.Success
          responseEntity.toString.asJson.as[MemberEventResponse].map { response =>
            val registered = response.asInstanceOf[MemberRegistered]
            registered.memberId shouldEqual memberId
            registered.memberInfo shouldEqual info
            registered.metaInfo.createdBy shouldEqual registeringMember
          }
        }
      }
    }
  }
}
