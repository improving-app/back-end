package com.improving.app.tenant

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.tenant.api.TenantService
import com.improving.app.tenant.domain._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TenantServiceImpl(sys: ActorSystem[_]) extends TenantService {
  private implicit val system: ActorSystem[_] = sys
  implicit val timeout: Timeout = 5.minute
  implicit val executor = system.executionContext

  val sharding = ClusterSharding(system)

  sharding.init(Entity(Tenant.TypeKey)(
    createBehavior = entityContext =>
      Tenant(
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
      )
  ))

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  private def exceptionHandler(exception: Throwable): GrpcServiceException = {
    GrpcServiceException(
      code = Code.INVALID_ARGUMENT,
      message = exception.getMessage,
      details = Seq(new LocalizedMessage("EN", exception.getMessage))
    )
  }

  override def establishTenant(in: EstablishTenant): Future[TenantEstablished] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantEstablishedValue,
      exception => exceptionHandler(exception)
    )
  }

  override def editInfo(in: EditInfo): Future[InfoEdited] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))

    result.transform(
      result => result.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getInfoEditedValue,
      exception => exceptionHandler(exception)
    )
  }

  override def activateTenant(in: ActivateTenant): Future[TenantActivated] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantActivatedValue,
      exception => exceptionHandler(exception)
    )
  }

  override def suspendTenant(in: SuspendTenant): Future[TenantSuspended] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.getTenantEventValue.tenantEvent.asMessage.getTenantSuspendedValue,
      exception => exceptionHandler(exception)
    )
  }

  override def getOrganizations(in: GetOrganizations): Future[OrganizationData] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.getTenantDataValue.tenantData.asMessage.getOrganizationDataValue,
      exception => exceptionHandler(exception)
    )
  }
}
