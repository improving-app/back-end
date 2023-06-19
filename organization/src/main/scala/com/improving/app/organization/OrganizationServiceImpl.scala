package com.improving.app.organization

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.common.domain.ContactList
import com.improving.app.organization.api.OrganizationService
import com.improving.app.organization.domain.{
  ActivateOrganization,
  AddMembersToOrganization,
  AddOwnersToOrganization,
  EstablishOrganization,
  GetOrganizationContacts,
  GetOrganizationInfo,
  MembersAddedToOrganization,
  MembersRemovedFromOrganization,
  Organization,
  OrganizationActivated,
  OrganizationContactsResponse,
  OrganizationContactsUpdated,
  OrganizationEstablished,
  OrganizationEvent,
  OrganizationEventPB,
  OrganizationInfo,
  OrganizationInfoResponse,
  OrganizationRequest,
  OrganizationRequestPB,
  OrganizationResponse,
  OrganizationSuspended,
  OrganizationTerminated,
  OwnersAddedToOrganization,
  OwnersRemovedFromOrganization,
  RemoveMembersFromOrganization,
  RemoveOwnersFromOrganization,
  SuspendOrganization,
  TerminateOrganization,
  UpdateOrganizationContacts
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
      request: OrganizationRequest with OrganizationRequestPB
  ): Future[ResultT] = request.organizationId
    .map { id =>
      val result = sharding
        .entityRefFor(Organization.TypeKey, id.id)
        .ask(ref => Organization.OrganizationRequestEnvelope(request.asInstanceOf[OrganizationRequestPB], ref))
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

  override def establishOrganization(command: EstablishOrganization): Future[OrganizationEstablished] =
    processEntityRequest(command)

  override def activateOrganization(command: ActivateOrganization): Future[OrganizationActivated] =
    processEntityRequest(command)

  override def suspendOrganization(command: SuspendOrganization): Future[OrganizationSuspended] =
    processEntityRequest(command)

  override def terminateOrganization(command: TerminateOrganization): Future[OrganizationTerminated] =
    processEntityRequest(command)

  override def addMembersToOrganization(command: AddMembersToOrganization): Future[MembersAddedToOrganization] =
    processEntityRequest(command)

  override def removeMembersFromOrganization(
      command: RemoveMembersFromOrganization
  ): Future[MembersRemovedFromOrganization] =
    processEntityRequest(command)

  override def addOwnersToOrganization(command: AddOwnersToOrganization): Future[OwnersAddedToOrganization] =
    processEntityRequest(command)

  override def removeOwnersFromOrganization(
      command: RemoveOwnersFromOrganization
  ): Future[OwnersRemovedFromOrganization] =
    processEntityRequest(command)

  override def getOrganizationInfo(query: GetOrganizationInfo): Future[OrganizationInfo] = {
    processEntityRequest[OrganizationInfoResponse](query).map(_.getInfo)
  }

  override def updateOrganizationContacts(query: UpdateOrganizationContacts): Future[OrganizationContactsUpdated] = {
    processEntityRequest[OrganizationContactsUpdated](query)
  }

  override def getOrganizationContacts(query: GetOrganizationContacts): Future[ContactList] = {
    processEntityRequest[OrganizationContactsResponse](query).map(r => ContactList(r.contacts))
  }
}
