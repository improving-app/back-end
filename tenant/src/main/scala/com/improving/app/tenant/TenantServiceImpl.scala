package com.improving.app.tenant

import akka.actor.typed.ActorSystem
import akka.cluster.typed.{Cluster, Join}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.util.Timeout
import akka.grpc.GrpcServiceException
import akka.persistence.typed.PersistenceId
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.tenant.api.TenantService
import com.improving.app.tenant.domain.{ActivateTenant, AddOrganizations, AddressUpdated, EstablishTenant, Info, OrganizationsAdded, OrganizationsRemoved, PrimaryContactUpdated, RemoveOrganizations, SuspendTenant, Tenant, TenantActivated, TenantEstablished, TenantNameUpdated, TenantSuspended, UpdateAddress, UpdatePrimaryContact, UpdateTenantName}

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
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        new Info() // TODO: obtain correct Info
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
      result => result.getValue.asMessage.sealedValue.tenantEstablishedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def updateTenantName(in: UpdateTenantName): Future[TenantNameUpdated] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.tenantNameUpdatedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def updatePrimaryContact(in: UpdatePrimaryContact): Future[PrimaryContactUpdated] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.primaryContactUpdatedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def updateAddress(in: UpdateAddress): Future[AddressUpdated] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.addressUpdatedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def addOrganizations(in: AddOrganizations): Future[OrganizationsAdded] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.organizationsAddedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def removeOrganizations(in: RemoveOrganizations): Future[OrganizationsRemoved] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.organizationsRemovedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def activateTenant(in: ActivateTenant): Future[TenantActivated] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.tenantActivatedValue.get,
      exception => exceptionHandler(exception)
    )
  }

  override def suspendTenant(in: SuspendTenant): Future[TenantSuspended] = {
    val result = sharding.entityRefFor(Tenant.TypeKey, in.tenantId.get.id)
      .ask(ref => Tenant.TenantCommand(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.tenantSuspendedValue.get,
      exception => exceptionHandler(exception)
    )
  }
}
