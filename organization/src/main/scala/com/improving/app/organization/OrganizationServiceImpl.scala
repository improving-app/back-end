package com.improving.app.organization

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.common.errors.ValidationError
import com.improving.app.organization.api.OrganizationService
import com.improving.app.organization.domain.{
  ActivateOrganization,
  EstablishOrganization,
  GetOrganizationInfo,
  Organization,
  OrganizationActivated,
  OrganizationEstablished,
  OrganizationInfo,
  OrganizationSuspended,
  OrganizationTerminated,
  SuspendOrganization,
  TerminateOrganization
}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt

class OrganizationServiceImpl(sys: ActorSystem[_]) extends OrganizationService {
  implicit private val system: ActorSystem[_] = sys
  implicit val timeout: Timeout = 5.minute
  implicit val executor: ExecutionContextExecutor = system.executionContext

  val sharding: ClusterSharding = ClusterSharding(system)

  sharding.init(
    Entity(Organization.TypeKey)(
      createBehavior = entityContext =>
        Organization(
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

  override def establishOrganization(in: EstablishOrganization): Future[OrganizationEstablished] = {
    val result = sharding
      .entityRefFor(Organization.TypeKey, in.organizationId.id)
      .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.organizationEstablished.get,
      exception => exceptionHandler(exception)
    )
  }

  override def activateOrganization(in: ActivateOrganization): Future[OrganizationActivated] = {
    val result = sharding
      .entityRefFor(Organization.TypeKey, in.organizationId.id)
      .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.organizationActivated.get,
      exception => exceptionHandler(exception)
    )
  }

  override def suspendOrganization(in: SuspendOrganization): Future[OrganizationSuspended] = {
    val result = sharding
      .entityRefFor(Organization.TypeKey, in.organizationId.id)
      .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.organizationSuspended.get,
      exception => exceptionHandler(exception)
    )
  }

  override def terminateOrganization(in: TerminateOrganization): Future[OrganizationTerminated] = {
    val result = sharding
      .entityRefFor(Organization.TypeKey, in.organizationId.id)
      .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
    result.transform(
      result => result.getValue.asMessage.sealedValue.organizationTerminated.get,
      exception => exceptionHandler(exception)
    )
  }

  override def getOrganizationInfo(in: GetOrganizationInfo): Future[OrganizationInfo] = ???
}
