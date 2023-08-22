package com.improving.app.gateway.infrastructure

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.grpc.GrpcServiceException
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.BadRequest
import com.improving.app.gateway.api.handlers._
import com.improving.app.gateway.infrastructure.routes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import akka.http.scaladsl.server.Directives.complete
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class GatewayServerImpl(implicit val sys: ActorSystem[_])
    extends TenantGatewayRoutes
    with OrganizationGatewayRoutes
    with MemberGatewayRoutes
    with EventGatewayRoutes
    with StoreGatewayRoutes
    with ProductGatewayRoutes {

  override val config: Config = ConfigFactory
    .load("application.conf")
    .withFallback(ConfigFactory.defaultApplication())

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler { case e: GrpcServiceException =>
      complete(
        HttpResponse(
          BadRequest,
          entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Json.fromString(e.getMessage).toString())
        )
      )
    }

  private val tenantHandler: TenantGatewayHandler = new TenantGatewayHandler()
  private val organizationHandler: OrganizationGatewayHandler = new OrganizationGatewayHandler()
  private val memberHandler: MemberGatewayHandler = new MemberGatewayHandler()
  private val eventHandler: EventGatewayHandler = new EventGatewayHandler()
  private val storeHandler: StoreGatewayHandler = new StoreGatewayHandler()
  private val productHandler: ProductGatewayHandler = new ProductGatewayHandler()

  implicit val dispatcher: ExecutionContextExecutor = sys.dispatchers.lookup(DispatcherSelector.defaultDispatcher())

  private val binding = Http()
    .newServerAt(config.getString("akka.http.interface"), config.getInt("akka.http.port"))
    .bindFlow(
      Directives
        .concat(
          tenantRoutes(tenantHandler),
          organizationRoutes(organizationHandler),
          memberRoutes(memberHandler),
          eventRoutes(eventHandler),
          storeRoutes(storeHandler),
          productRoutes(productHandler)
        )
    )

  def start(): Unit = binding.onComplete {
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
