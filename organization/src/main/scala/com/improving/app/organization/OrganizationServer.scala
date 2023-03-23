package com.improving.app.organization

//#import

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.organization.repository.OrganizationRepository
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object OrganizationServer {

  def start(
      interface: String,
      port: Int,
      system: ActorSystem[_],
      grpcService: OrganizationService
  ): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext =
      system.executionContext

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        OrganizationServiceHandler.partial(grpcService),
        // ServerReflection enabled to support grpcurl without import-path and proto parameters
        ServerReflection.partial(List(OrganizationService))
      )

    val terminationDateLine = system.settings.config.getDuration("organization-service.hard-termination-deadline")

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = interface, port = port)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = terminationDateLine.toSeconds.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "Organization Service online at gRPC server {}:{}",
          address.getHostString,
          address.getPort
        )
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
  }
}

class OrganizationServer(interface: String, port: Int, system: ActorSystem[_], repo: OrganizationRepository) {

  private val log = LoggerFactory.getLogger(this.getClass)

  def run(): Future[Http.ServerBinding] = {
    implicit val sys = system
    implicit val ec: ExecutionContext = system.executionContext
    implicit val db: OrganizationRepository = repo

    val service: HttpRequest => Future[HttpResponse] =
      OrganizationServiceHandler(new OrganizationServiceImpl())

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = interface, port = port)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        log.info(
          s"gRPC server bound to ${address.getHostString}:${address.getPort}"
        )
      case Failure(ex) =>
        log.error(s"Failed to bind gRPC endpoint, terminating system $ex")
        system.terminate()
    }

    bound
  }
}
