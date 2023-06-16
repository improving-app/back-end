package com.improving.app.gateway

import akka.actor.typed
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import com.improving.app.common.domain.MemberId
import com.improving.app.gateway.api.handlers.MemberGatewayHandler
import com.improving.app.gateway.domain.common.util.{
  editableMemberInfoToGatewayEditableInfo,
  notificationPreferenceToGatewayNotificationPreference
}
import com.improving.app.gateway.domain.{EditableMemberInfo, MemberRegistered, RegisterMember}
import com.improving.app.gateway.infrastructure.routes.MemberGatewayRoutes
import com.improving.app.member.domain.TestData.baseEditableInfo
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File
import java.util.UUID
import scala.language.postfixOps

class MemberGatewayServerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with BeforeAndAfterEach
    with MemberGatewayRoutes
    with TestContainerForAll {

  implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped

  override val config: Config = ConfigFactory.load("application.conf")

  override val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    new File("../docker-compose.yml"),
    tailChildContainers = true,
    exposedServices = Seq(
      ExposedService("member-service", 8081, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8081*.", 1))
    )
  )

  def getContainerHostPort(containers: Containers): (String, Integer) = {
    val host = containers.container.getServiceHost("member-service", 8081)
    val port = containers.container.getServicePort("member-service", 8081)
    (host, port)
  }

  private def getClient(containers: Containers): GrpcClientSettings = {
    val (host, port) = getContainerHostPort(containers)
    GrpcClientSettings.connectToServiceAt(host, port)(system).withTls(false)
  }

  def validateExposedPort(a: Containers): Unit = {
    assert(a.container.getServicePort("member-service", 8081) > 0)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "In MemberGateway" when {
    "starting up" should {
      "retrieve port for service" in {
        withContainers { container =>
          validateExposedPort(container)
        }
      }
    }
    "Sending RegisterMember" should {
      "succeed on golden path" in {
        withContainers { container =>
          val handler: MemberGatewayHandler =
            new MemberGatewayHandler(grpcClientSettingsOpt = Some(getClient(container)))

          val memberId = UUID.randomUUID().toString
          val registeringMember = UUID.randomUUID().toString

          val info = baseEditableInfo

          val command = RegisterMember(
            Some(MemberId.of(memberId)),
            Some(
              EditableMemberInfo(
                info.handle,
                info.avatarUrl,
                info.firstName,
                info.lastName,
                info.notificationPreference.map(notificationPreferenceToGatewayNotificationPreference),
                info.contact,
                info.organizationMembership,
                info.tenant
              )
            ),
            Some(MemberId.of(registeringMember))
          )
          Post("/member", command.toProtoString) ~> Route.seal(
            routes(handler)
          ) ~> check {
            status shouldBe StatusCodes.OK
            val response = MemberRegistered.fromAscii(responseAs[String])
            response.memberId.map(_.id) shouldEqual Some(memberId)
            response.memberInfo shouldEqual Some(editableMemberInfoToGatewayEditableInfo(info))
            response.meta.flatMap(_.createdBy.map(_.id)) shouldEqual Some(registeringMember)
          }
        }
      }
    }
  }
}
