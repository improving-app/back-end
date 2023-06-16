package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.gateway.domain.common.tenantUtil.{
  editableTenantInfoToGatewayEditableInfo,
  gatewayEditableTenantInfoToEditableInfo,
  tenantMetaToGatewayTenantMeta
}
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.tenant.{TenantEstablished, EstablishTenant => GatewayEstablishTenant}
import com.improving.app.tenant.api.TenantServiceClient
import com.improving.app.tenant.domain.EstablishTenant
import com.typesafe.scalalogging.StrictLogging
import com.improving.app.gateway.api.handlers.errors.handlers.exceptionHandler

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
          in.getTenantId,
          in.getEstablishingUser,
          Some(gatewayEditableTenantInfoToEditableInfo(in.getTenantInfo))
        )
      )
      .map { response =>
        TenantEstablished(
          Some(response.tenantId),
          Some(tenantMetaToGatewayTenantMeta(response.metaInfo)),
          response.tenantInfo.map(editableTenantInfoToGatewayEditableInfo)
        )
      }
}
