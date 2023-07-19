package com.improving.app.gateway.api.handlers

import com.improving.app.gateway.domain.orgUtil.{
  EditableOrganizationInfoUtil,
  GatewayEditableOrganizationInfoUtil,
  OrganizationMetaInfoUtil
}
import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.organization.{
  ActivateOrganization => GatewayActivateOrganization,
  EstablishOrganization => GatewayEstablishOrganization,
  OrganizationActivated,
  OrganizationEstablished,
  OrganizationTerminated,
  TerminateOrganization => GatewayTerminateOrganization
}
import com.improving.app.organization.api.OrganizationServiceClient
import com.improving.app.organization.domain.{ActivateOrganization, EstablishOrganization, TerminateOrganization}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class OrganizationGatewayHandler(grpcClientSettingsOpt: Option[GrpcClientSettings] = None)(implicit
    val system: ActorSystem[_]
) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = getHostAndPortForService("organization-service")

  private val orgClient: OrganizationServiceClient = OrganizationServiceClient(
    grpcClientSettingsOpt.getOrElse(
      GrpcClientSettings
        .connectToServiceAt(clientHost, clientPort)
        .withTls(false)
    )
  )

  def establishOrganization(in: GatewayEstablishOrganization): Future[OrganizationEstablished] =
    orgClient
      .establishOrganization(
        EstablishOrganization(
          in.organizationId,
          in.onBehalfOf,
          Some(in.getOrganizationInfo.toEditable)
        )
      )
      .map { response =>
        OrganizationEstablished(
          Some(response.getOrganizationId),
          Some(response.getMetaInfo.toGatewayMeta),
          response.organizationInfo.map(_.toGatewayEditable)
        )
      }

  def activateOrganization(in: GatewayActivateOrganization): Future[OrganizationActivated] =
    orgClient
      .activateOrganization(
        ActivateOrganization(
          in.organizationId,
          in.onBehalfOf
        )
      )
      .map { response =>
        OrganizationActivated(
          Some(response.getOrganizationId),
          Some(response.getMetaInfo.toGatewayMeta),
        )
      }

  def terminateOrganization(in: GatewayTerminateOrganization): Future[OrganizationTerminated] =
    orgClient
      .terminateOrganization(
        TerminateOrganization(
          in.organizationId,
          in.onBehalfOf
        )
      )
      .map { response =>
        OrganizationTerminated(
          Some(response.getOrganizationId)
        )
      }
}
