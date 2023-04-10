package com.improving.app.gateway

import akka.actor.{typed, Scheduler}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.Random
import com.improving.app.gateway.domain.common.Contact
import com.improving.app.gateway.domain.MemberInfo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.implicits.toFunctorOps
import com.improving.app.gateway.domain.MemberMessages.{
  MemberData,
  MemberEventResponse,
  MemberRegistered,
  MemberResponse,
  RegisterMember
}
import com.improving.app.gateway.domain.NotificationPreference.EMAIL_NOTIFICATION_PREFERENCE
import com.improving.app.gateway.infrastructure.GatewayServerImpl
import io.circe.Decoder
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures

//import scala.language.postfixOps
import java.util.UUID

class GatewayServerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with BeforeAndAfterEach {

  implicit val decodeMemberResponse: Decoder[MemberResponse] =
    List[Decoder[MemberResponse]](
      Decoder[MemberRegistered].widen
    ).reduceLeft(_ or _)

  implicit val decodeMemberEventResponse: Decoder[MemberEventResponse] =
    List[Decoder[MemberEventResponse]](
      Decoder[MemberResponse].widen,
      Decoder[MemberData].widen
    ).reduceLeft(_ or _)

  implicit val timeout: Timeout = Timeout(500.milliseconds)

  implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped
  private val server: GatewayServerImpl = new GatewayServerImpl()

  implicit val scheduler: Scheduler = system.scheduler

  override def beforeEach(): Unit = server.start()

  "In MemberGateway" when {
    "Sending RegisterMember" should {
      "succeed" in {
        val info = MemberInfo(
          handle = Random.nextString(31),
          avatarUrl = Random.nextString(31),
          firstName = Random.nextString(31),
          lastName = Random.nextString(31),
          notificationPreference = EMAIL_NOTIFICATION_PREFERENCE,
          notificationOptIn = true,
          contact = Contact(
            firstName = Random.nextString(31),
            lastName = Random.nextString(31),
            emailAddress = Some(Random.nextString(31)),
            phone = Some(Random.nextString(31)),
            userName = Random.nextString(31)
          ),
          organizations = Seq(UUID.randomUUID()),
          tenant = UUID.randomUUID()
        )

        val registeringMember = UUID.randomUUID()

        val command = RegisterMember(UUID.randomUUID(), info, registeringMember)

        Post("/registerMember", command.asJson.toString()) -> Route.seal(server.routes) -> check {
          status shouldEqual StatusCodes.Success
          responseEntity.toString.asJson.as[MemberEventResponse].map { response =>
            val registered = response.asInstanceOf[MemberRegistered]
            registered.memberInfo shouldEqual info
            registered.actingMember shouldEqual registeringMember
          }
        }
      }
    }
  }
}
