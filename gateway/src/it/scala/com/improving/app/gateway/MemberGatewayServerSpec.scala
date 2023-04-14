package com.improving.app.gateway

import akka.actor.typed
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import cats.implicits.toFunctorOps
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.GenericContainer
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.MemberMessages._
import com.improving.app.gateway.domain.common.util.memberInfoToGatewayMemberInfo
import com.improving.app.gateway.infrastructure.routes.MemberGatewayRoutes
import com.improving.app.member.domain.TestData.baseMemberInfo
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalatest.Retries.{isRetryable, withRetry}
import org.scalatest.{BeforeAndAfterEach, Outcome}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class MemberGatewayServerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with BeforeAndAfterEach
    with MemberGatewayRoutes
    with TestContainerForAll {
  override def withFixture(test: NoArgTest): Outcome = {
    if (isRetryable(test))
      withRetry {
        super.withFixture(test)
      }
    else
      super.withFixture(test)
  }

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

  implicit override val handler: MemberGatewayHandler = new MemberGatewayHandler(
    ("localhost", 8081)
  )

  override lazy val containerDef: GenericContainer.Def[GenericContainer] = GenericContainer.Def(
    "improving-app-member:latest",
    exposedPorts = Seq(8081),
  )

  val container: GenericContainer = containerDef.start()

  override def beforeAll(): Unit = {
    super.beforeAll()
    container.start()
  }
  override def afterAll(): Unit = {
    super.afterAll()
    container.stop()
    system.terminate()
  }

  "In MemberGateway" when {
    "starting up" should {
      "retrieve port for service" taggedAs Retryable in {
        withContainers { a =>
          assert(a.container.getExposedPorts.size > 0)
        }
      }
    }
    "Sending RegisterMember" should {
      "succeed" taggedAs Retryable in {
        val info = memberInfoToGatewayMemberInfo(baseMemberInfo)

        val memberId = UUID.randomUUID()
        val registeringMember = UUID.randomUUID()

        val command = RegisterMember(memberId, info, registeringMember)

        Post("/member", command.asInstanceOf[MemberCommand].asJson) ~> Route.seal(
          routes
        ) ~> check {
          status shouldBe StatusCodes.OK
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
