package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.tenantUtil.{
  editableTenantInfoToGatewayEditableInfo,
  gatewayEditableTenantInfoToEditableInfo,
  tenantMetaToGatewayTenantMeta
}
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.tenant.{
  ActivateTenant => GatewayActivateTenant,
  EstablishTenant => GatewayEstablishTenant,
  TenantActivated,
  TenantEstablished,
  TenantTerminated,
  TerminateTenant => GatewayTerminateTenant
}
import com.improving.app.tenant.api.TenantServiceClient
import com.improving.app.tenant.domain.{ActivateTenant, EstablishTenant, TerminateTenant}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class TenantGatewayHandler(grpcClientSettingsOpt: Option[GrpcClientSettings] = None)(implicit
    val system: ActorSystem[_]
) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = getHostAndPortForService("tenant-service")

  private val tenantClient: TenantServiceClient = TenantServiceClient(
    grpcClientSettingsOpt.getOrElse(
      GrpcClientSettings
        .connectToServiceAt(clientHost, clientPort)
        .withTls(false)
    )
  )

  def establishTenant(in: GatewayEstablishTenant): Future[TenantEstablished] =
    tenantClient
      .establishTenant(
        EstablishTenant(
          in.tenantId,
          in.onBehalfOf,
          Some(gatewayEditableTenantInfoToEditableInfo(in.getTenantInfo))
        )
      )
      .map { response =>
        TenantEstablished(
          Some(response.getTenantId),
          Some(tenantMetaToGatewayTenantMeta(response.getMetaInfo)),
          response.tenantInfo.map(editableTenantInfoToGatewayEditableInfo)
        )
      }

  def activateTenant(in: GatewayActivateTenant): Future[TenantActivated] =
    tenantClient
      .activateTenant(
        ActivateTenant(
          in.tenantId,
          in.onBehalfOf
        )
      )
      .map { response =>
        TenantActivated(
          Some(response.getTenantId),
          Some(tenantMetaToGatewayTenantMeta(response.getMetaInfo)),
        )
      }

  def terminateTenant(in: GatewayTerminateTenant): Future[TenantTerminated] =
    tenantClient
      .terminateTenant(
        TerminateTenant(
          in.tenantId,
          in.onBehalfOf
        )
      )
      .map { response =>
        TenantTerminated(
          Some(response.getTenantId),
          Some(tenantMetaToGatewayTenantMeta(response.getMetaInfo)),
        )
      }
}
