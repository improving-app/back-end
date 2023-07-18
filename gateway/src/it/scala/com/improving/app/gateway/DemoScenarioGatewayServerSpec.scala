package com.improving.app.gateway

import akka.actor.typed
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import com.improving.app.gateway.api.handlers.{MemberGatewayHandler, OrganizationGatewayHandler, TenantGatewayHandler}
import com.improving.app.gateway.domain.demoScenario.{ScenarioStarted, StartScenario}
import com.improving.app.gateway.infrastructure.routes.DemoScenarioGatewayRoutes
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File
import scala.language.{existentials, postfixOps}

class DemoScenarioGatewayServerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with BeforeAndAfterEach
    with DemoScenarioGatewayRoutes
    with TestContainerForAll {

  implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped

  override val config: Config = ConfigFactory.load("application.conf")

  override val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    new File("../docker-compose.yml"),
    tailChildContainers = true,
    exposedServices = Seq(
      ExposedService("tenant-service", 8080, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8080*.", 1)),
      ExposedService("member-service", 8081, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8081*.", 1)),
      ExposedService("organization-service", 8082, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8082*.", 1)),
      ExposedService("store-service", 8081, Wait.forLogMessage(s".*gRPC server bound to 0.0.0.0:8083*.", 1))
    )
  )

  def getContainerHostPort(containers: Containers, serviceName: String, port: Int): (String, Integer) = {
    val hostDef = containers.container.getServiceHost(serviceName, port)
    val portDef = containers.container.getServicePort(serviceName, port)
    (hostDef, portDef)
  }

  private def getClient(containers: Containers, serviceName: String, port: Int): GrpcClientSettings = {
    val (hostDef, portDef) = getContainerHostPort(containers, serviceName: String, port: Int)
    GrpcClientSettings.connectToServiceAt(hostDef, portDef)(system).withTls(false)
  }

  def validateExposedPort(a: Containers): Unit = {
    assert(a.container.getServicePort("member-service", 8081) > 0)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "In DemoScenarioGateway" when {
    "starting up" should {
      "retrieve port for service" in {
        withContainers { container =>
          validateExposedPort(container)
        }
      }
    }
    "Sending StartScenario" should {
      "succeed on golden path" in {
        withContainers { container =>
          val tenantHandler: TenantGatewayHandler =
            new TenantGatewayHandler(grpcClientSettingsOpt = Some(getClient(container, "tenant-service", 8080)))
          val memberHandler: MemberGatewayHandler =
            new MemberGatewayHandler(grpcClientSettingsOpt = Some(getClient(container, "member-service", 8081)))
          val orgHandler: OrganizationGatewayHandler =
            new OrganizationGatewayHandler(grpcClientSettingsOpt =
              Some(getClient(container, "organization-service", 8082))
            )

          val command = StartScenario(
            numTenants = 1,
            numMembersPerOrg = 1
          )
          Post("/member", command.toProtoString) ~> Route.seal(
            demoScenarioRoutes(tenantHandler, orgHandler, memberHandler)
          ) ~> check {
            status shouldBe StatusCodes.OK
            val response = ScenarioStarted.fromAscii(responseAs[String])
            response.tenants.length shouldEqual 1
            response.organizations.length shouldEqual 1
          }
        }
      }
    }
  }
}
