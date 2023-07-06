package com.improving.app.gateway.infrastructure

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.settings.{ParserSettings, ServerSettings}
import com.improving.app.gateway.api.handlers.{MemberGatewayHandler, OrganizationGatewayHandler, TenantGatewayHandler}
import akka.http.scaladsl.server.Directives
import com.improving.app.gateway.api.handlers.{MemberGatewayHandler, TenantGatewayHandler}
import com.improving.app.gateway.infrastructure.routes.{DemoScenarioGatewayRoutes, MemberGatewayRoutes}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class GatewayServerImpl(implicit val sys: ActorSystem[_]) extends MemberGatewayRoutes with DemoScenarioGatewayRoutes {

  override val config: Config = ConfigFactory
    .load("application.conf")
    .withFallback(ConfigFactory.defaultApplication())

  private val tenantHandler: TenantGatewayHandler = new TenantGatewayHandler()
  private val organizationHandler:  OrganizationGatewayHandler = new OrganizationGatewayHandler()
  private val memberHandler: MemberGatewayHandler = new MemberGatewayHandler()

  implicit val dispatcher: ExecutionContextExecutor = sys.dispatchers.lookup(DispatcherSelector.defaultDispatcher())

  private val binding = Http()
    .newServerAt(config.getString("akka.http.interface"), config.getInt("akka.http.port"))
    .bindFlow(Directives.concat(memberRoutes(memberHandler), demoScenarioRoutes(tenantHandler, organizationHandler)))

  def start(): Unit = binding
    .onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        println(s"Improving.App HTTP Gateway bound to ${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        println(s"Failed to bind HTTP endpoint for Improving.APP Gateway, terminating system: ${ex.getMessage}")
        sys.terminate()
    }

  def tearDown: Future[Unit] = Await
    .result(binding, 10.seconds)
    .terminate(hardDeadline = 5.seconds)
    .flatMap { _ =>
      Future(sys.terminate())
    }
}
