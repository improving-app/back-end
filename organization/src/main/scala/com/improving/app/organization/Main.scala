package com.improving.app.organization

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.improving.app.organization.repository._
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object Main {

  val logger = LoggerFactory.getLogger("organization.Main")

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system =
      ActorSystem[Nothing](
        Behaviors.empty,
        "OrganizationService",
        conf.resolve()
      )
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(implicit system: ActorSystem[_]): Unit = {

    val session = CassandraSessionRegistry(system).sessionFor(
      "akka.persistence.cassandra"
    )

    val OrganizationServiceKeyspace =
      system.settings.config
        .getString("akka.projection.cassandra.offset-store.keyspace")
    implicit val organizationRepository =
      new OrganizationRepositoryImpl(session, OrganizationServiceKeyspace)(
        system.executionContext
      )

    domain.Organization.init(system)

    OrganizationByMemberProjection.init(system, organizationRepository)
    OrganizationByOwnerProjection.init(system, organizationRepository)
    OrganizationToRootProjection.init(system, organizationRepository)
    OrganizationByChildrenProjection.init(system, organizationRepository)

    val grpcInterface =
      system.settings.config.getString(
        "akka.grpc.client.organization-config.host"
      )
    val grpcPort =
      system.settings.config.getInt("akka.grpc.client.organization-config.port")
    val grpcService =
      new OrganizationServiceImpl()
    OrganizationServer.start(grpcInterface, grpcPort, system, grpcService)
  }

}
