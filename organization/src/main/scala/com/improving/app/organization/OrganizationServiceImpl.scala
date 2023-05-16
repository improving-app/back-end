package com.improving.app.organization

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.common.errors.ValidationError
import com.improving.app.organization.api.OrganizationService
import com.improving.app.organization.domain.{
  ActivateOrganization,
  AddMembersToOrganization,
  AddOwnersToOrganization,
  EstablishOrganization,
  GetOrganizationInfo,
  MembersAddedToOrganization,
  MembersRemovedFromOrganization,
  Organization,
  OrganizationActivated,
  OrganizationEstablished,
  OrganizationEvent,
  OrganizationInfo,
  OrganizationRequest,
  OrganizationRequestPB,
  OrganizationSuspended,
  OrganizationTerminated,
  OwnersAddedToOrganization,
  OwnersRemovedFromOrganization,
  RemoveMembersFromOrganization,
  RemoveOwnersFromOrganization,
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

  private def processEntityRequest[ResultT](
      request: OrganizationRequest,
      resultTransformer: StatusReply[OrganizationEvent] => ResultT
  ): Future[ResultT] = {
      val result = sharding
        .entityRefFor(Organization.TypeKey, request.organizationId.id)
        .ask(ref => Organization.OrganizationRequestEnvelope(request.asInstanceOf[OrganizationRequestPB], ref))
      result.transform(
        resultTransformer,
        exception => exceptionHandler(exception)
      )
  }

  override def establishOrganization(command: EstablishOrganization): Future[OrganizationEstablished] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.organizationEstablished.get)

  override def activateOrganization(command: ActivateOrganization): Future[OrganizationActivated] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.organizationActivated.get)

  override def suspendOrganization(command: SuspendOrganization): Future[OrganizationSuspended] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.organizationSuspended.get)

  override def terminateOrganization(command: TerminateOrganization): Future[OrganizationTerminated] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.organizationTerminated.get)

  override def addMembersToOrganization(command: AddMembersToOrganization): Future[MembersAddedToOrganization] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.membersAddedToOrganization.get)

  override def removeMembersFromOrganization(
      command: RemoveMembersFromOrganization
  ): Future[MembersRemovedFromOrganization] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.membersRemovedFromOrganization.get)

  override def addOwnersToOrganization(command: AddOwnersToOrganization): Future[OwnersAddedToOrganization] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.ownersAddedToOrganization.get)

  override def removeOwnersFromOrganization(
      command: RemoveOwnersFromOrganization
  ): Future[OwnersRemovedFromOrganization] =
    processEntityRequest(command, result => result.getValue.asMessage.sealedValue.ownersRemovedFromOrganization.get)

  override def getOrganizationInfo(query: GetOrganizationInfo): Future[OrganizationInfo] = ???
}
