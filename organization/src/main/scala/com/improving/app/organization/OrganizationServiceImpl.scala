package com.improving.app.organization

//#import
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.pattern.StatusReply
import com.improving.app.organization.domain.Organization.{HasOrganizationId, OrganizationCommand}
import akka.util.Timeout
import com.improving.app.organization.domain.Organization.OrganizationEntityKey
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class OrganizationServiceImpl(implicit val system: ActorSystem[_]) extends OrganizationService {

  implicit private val ec: ExecutionContext = system.executionContext
  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("organization-service.ask-timeout")
    )

  private val log = LoggerFactory.getLogger(getClass)

  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(OrganizationEntityKey)(entityContext => domain.Organization(entityContext.entityId))
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  private def handleResponse[T](
      eventHandler: PartialFunction[StatusReply[OrganizationResponse], T]
  ): PartialFunction[StatusReply[OrganizationResponse], T] = {
    eventHandler.orElse({
      case StatusReply.Success(response) =>
        throw new RuntimeException(s"Unexpected response $response")
      case StatusReply.Error(ex) => throw ex
    })
  }

  private def handleRequest[T](
      in: OrganizationRequest,
      eventHandler: PartialFunction[StatusReply[OrganizationResponse], T],
      extractOrganizationId: OrganizationRequest => String = {
        case req: HasOrganizationId => req.extractOrganizationId
        case other =>
          throw new RuntimeException(
            s"Organization request does not implement HasOrganizationId $other"
          )
      }
  ) = {
    val organizationEntity =
      sharding.entityRefFor(OrganizationEntityKey, extractOrganizationId(in))

    organizationEntity
      .ask[StatusReply[OrganizationResponse]](replyTo => OrganizationCommand(in, replyTo))
      .map {
        handleResponse(eventHandler)
      }
  }

  override def establishOrganization(
      request: EstablishOrganizationRequest
  ): Future[OrganizationEstablished] = {
    log.info("OrganizationServiceImpl: establishOrganization")
    handleRequest(
      request,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationEstablished,
                _
              )
            ) =>
          response
      },
      _ => {
        UUID.randomUUID().toString
      }
    )
  }

  override def getOrganization(
      in: GetOrganizationByIdRequest
  ): Future[Organization] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              response: Organization
            ) =>
          response
      }
    )
  }

  override def getOrganizationInfo(
      in: GetOrganizationInfoRequest
  ): Future[OrganizationInfo] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              response: OrganizationInfo
            ) =>
          response
      }
    )
  }

  override def addMembersToOrganization(
      in: AddMembersToOrganizationRequest
  ): Future[MembersAddedToOrganization] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: MembersAddedToOrganization,
                _
              )
            ) =>
          response
      }
    )
  }

  override def removeMembersFromOrganization(
      in: RemoveMembersFromOrganizationRequest
  ): Future[MembersRemovedFromOrganization] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: MembersRemovedFromOrganization,
                _
              )
            ) =>
          response
      }
    )
  }

  override def addOwnersToOrganization(
      in: AddOwnersToOrganizationRequest
  ): Future[OwnersAddedToOrganization] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OwnersAddedToOrganization,
                _
              )
            ) =>
          response
      }
    )
  }

  override def removeOwnersFromOrganization(
      in: RemoveOwnersFromOrganizationRequest
  ): Future[OwnersRemovedFromOrganization] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OwnersRemovedFromOrganization,
                _
              )
            ) =>
          response
      }
    )
  }

  override def editOrganizationInfo(
      in: EditOrganizationInfoRequest
  ): Future[OrganizationInfoUpdated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationInfoUpdated,
                _
              )
            ) =>
          response
      }
    )
  }

  override def releaseOrganization(
      in: ReleaseOrganizationRequest
  ): Future[OrganizationReleased] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationReleased,
                _
              )
            ) =>
          response
      }
    )
  }

  override def updateParent(in: UpdateParentRequest): Future[ParentUpdated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: ParentUpdated,
                _
              )
            ) =>
          response
      }
    )
  }

  override def updateOrganizationStatus(
      in: UpdateOrganizationStatusRequest
  ): Future[OrganizationStatusUpdated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationStatusUpdated,
                _
              )
            ) =>
          response
      }
    )
  }

  override def updateOrganizationContacts(
      in: UpdateOrganizationContactsRequest
  ): Future[OrganizationContactsUpdated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationContactsUpdated,
                _
              )
            ) =>
          response
      }
    )
  }

  override def updateOrganizationAccounts(
      in: UpdateOrganizationAccountsRequest
  ): Future[OrganizationAccountsUpdated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationAccountsUpdated,
                _
              )
            ) =>
          response
      }
    )
  }

  override def activateOrganization(
      in: ActivateOrganizationRequest
  ): Future[OrganizationActivated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationActivated,
                _
              )
            ) =>
          response
      }
    )
  }

  override def suspendOrganization(
      in: SuspendOrganizationRequest
  ): Future[OrganizationSuspended] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationSuspended,
                _
              )
            ) =>
          response
      }
    )
  }

  override def terminateOrganization(
      in: TerminateOrganizationRequest
  ): Future[OrganizationTerminated] = {
    handleRequest(
      in,
      {
        case StatusReply.Success(
              OrganizationEventResponse(
                response: OrganizationTerminated,
                _
              )
            ) =>
          response
      }
    )
  }
}