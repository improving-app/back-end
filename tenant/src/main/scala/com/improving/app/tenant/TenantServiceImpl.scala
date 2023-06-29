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

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt

class TenantServiceImpl(sys: ActorSystem[_]) extends TenantService {
  implicit private val system: ActorSystem[_] = sys
  implicit val timeout: Timeout = 5.minute
  implicit val executor: ExecutionContextExecutor = system.executionContext

  val sharding: ClusterSharding = ClusterSharding(system)

  sharding.init(
    Entity(Tenant.TypeKey)(
      createBehavior = entityContext =>
        Tenant(
          PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
        )
    )
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  private def exceptionHandler(exception: Throwable): GrpcServiceException = {
    GrpcServiceException(
      code = Code.INVALID_ARGUMENT,
      message = exception.getMessage,
      details = Seq(new LocalizedMessage("EN", exception.getMessage))
    )
  }

  private def handleRequest[ResultT](
      request: TenantRequest with TenantRequestPB
  ): Future[ResultT] = request.tenantId
    .map { id =>
      val result = sharding
        .entityRefFor(Tenant.TypeKey, id.id)
        .ask(ref => Tenant.TenantRequestEnvelope(request.asInstanceOf[TenantRequestPB], ref))
      result.transform(
        _.getValue.asInstanceOf[ResultT],
        exception => exceptionHandler(exception)
      )
    }
    .getOrElse(
      Future.failed(
        GrpcServiceException.create(
          Code.INVALID_ARGUMENT,
          "An entity Id was not provided",
          java.util.List.of(request.asMessage)
        )
      )
    )

  override def establishTenant(in: EstablishTenant): Future[TenantEstablished] =
    handleRequest[TenantEventResponse](in).map(_.tenantEvent.asMessage.getTenantEstablishedValue)

  override def editInfo(in: EditInfo): Future[InfoEdited] =
    handleRequest[TenantEventResponse](in).map(_.tenantEvent.asMessage.getInfoEditedValue)

  override def activateTenant(in: ActivateTenant): Future[TenantActivated] =
    handleRequest[TenantEventResponse](in).map(_.tenantEvent.asMessage.getTenantActivatedValue)

  override def suspendTenant(in: SuspendTenant): Future[TenantSuspended] =
    handleRequest[TenantEventResponse](in).map(_.tenantEvent.asMessage.getTenantSuspendedValue)

  override def terminateTenant(in: TerminateTenant): Future[TenantTerminated] =
    handleRequest[TenantEventResponse](in).map(_.tenantEvent.asMessage.getTenantTerminatedValue)

  override def getOrganizations(in: GetOrganizations): Future[TenantOrganizationData] =
    handleRequest[TenantDataResponse](in).map(_.tenantData.asMessage.getOrganizationDataValue)

}
