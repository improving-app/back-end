package com.improving.app.organization.domain

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl._
import cats.data.Validated
import cats.implicits.toFoldableOps
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain._
import com.improving.app.organization._
import org.slf4j.LoggerFactory

object Organization {

  private val log =
    LoggerFactory.getLogger(
      "com.improving.app.organization.domain.Organization"
    )

  def now = java.time.Instant.now()

  trait HasOrganizationId {
    def orgId: Option[OrganizationId]

    def extractMemberId: String =
      orgId match {
        case Some(OrganizationId(id, _)) => id
        case other =>
          throw new RuntimeException(s"Unexpected request to extract id $other")
      }
  }

  val OrganizationEntityKey: EntityTypeKey[OrganizationCommand] =
    EntityTypeKey[OrganizationCommand]("Organization")

  final case class OrganizationCommand(
      request: OrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  )

  private def emptyState(entityId: String): OrganizationState =
    OrganizationState(Some(OrganizationId(entityId)))

  def apply(
      entityTypeHint: String,
      orgId: String
  ): Behavior[OrganizationCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting Organization {}", orgId)
      EventSourcedBehavior
        .withEnforcedReplies[
          OrganizationCommand,
          OrganizationEvent,
          OrganizationState
        ](
          persistenceId = PersistenceId(entityTypeHint, orgId),
          emptyState = emptyState(orgId),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Organization {} stopped", orgId)
        }
    }

  /**
   * @param actingMember
   * @return new MetaInfo with draft status
   */
  private def createMetaInfo(actingMember: Option[MemberId]): MetaInfo = {
    val timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
    MetaInfo(
      createdOn = Some(timestamp),
      createdBy = actingMember,
      lastUpdated = Some(timestamp),
      lastUpdatedBy = actingMember,
      currentStatus = OrganizationStatus.ORGANIZATION_STATUS_DRAFT,
      children = Seq.empty[OrganizationId]
    )
  }

  private def updateMetaInfo(
      metaInfo: MetaInfo,
      actingMember: Option[MemberId],
      newStatus: Option[OrganizationStatus] = None,
      children: Seq[OrganizationId] = Seq.empty[OrganizationId]
  ) = {
    val timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
    metaInfo.copy(
      lastUpdated = Some(timestamp),
      lastUpdatedBy = actingMember,
      currentStatus = newStatus.getOrElse(metaInfo.currentStatus),
      children = if (children.isEmpty) metaInfo.children else children
    )
  }

  private def updateInfo(info: Info, newInfo: Info): Info = {
    info.copy(
      name = newInfo.name,
      shortName = newInfo.shortName.orElse(info.shortName),
      address = newInfo.address.orElse(info.address),
      isPrivate = newInfo.isPrivate,
      url = newInfo.url.orElse(info.url),
      logo = newInfo.logo.orElse(info.logo),
      tenant = newInfo.tenant.orElse(info.tenant)
    )
  }

  /**
   * @param state
   * @param command
   * @return boolean - validate if state transition is valid for the command.
   */
  private def isCommandValidForState(
      state: OrganizationState,
      command: OrganizationRequest
  ): Boolean = {
    command match {
      case UpdateOrganizationContactsRequest(_, _, _, _) | UpdateOrganizationAccountsRequest(_, _, _, _) |
          UpdateParentRequest(_, _, _, _) | SuspendOrganizationRequest(_, _, _) |
          EditOrganizationInfoRequest(_, _, _, _) | RemoveOwnersFromOrganizationRequest(_, _, _, _) |
          AddOwnersToOrganizationRequest(_, _, _, _) | RemoveMembersFromOrganizationRequest(_, _, _, _) |
          AddMembersToOrganizationRequest(_, _, _, _) =>
        state.meta.exists(meta =>
          meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_DRAFT || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_ACTIVE
        )
      case UpdateOrganizationStatusRequest(_, _, _, _) =>
        state.meta.exists(meta =>
          !(meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_TERMINATED || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
        )
      case TerminateOrganizationRequest(_, _, _) =>
        state.meta.exists(meta =>
          !(meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_TERMINATED || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
        )
      case ActivateOrganizationRequest(_, _, _) =>
        state.meta.exists(meta =>
          meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_DRAFT || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED
        )
      case ReleaseOrganizationRequest(_, _, _) =>
        state.meta.exists(meta => meta.currentStatus != OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
      case GetOrganizationInfoRequest(_, _) =>
        state.meta.exists(meta =>
          !(meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_TERMINATED || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
        )
      case EstablishOrganizationRequest(_, _, _, _, _, _, _) =>
        state.meta.isEmpty
      case GetOrganizationByIdRequest(_, _) =>
        true
      case other =>
        log.error(s"Invalid Organization Command $other")
        false
    }
  }

  private def updateOrganizationContacts(
      orgId: Option[OrganizationId],
      contacts: Seq[Contacts],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationContactsUpdated(
      orgId,
      contacts,
      actingMember
    )
    log.info(
      s"updateOrganizationContacts: orgId $orgId contacts $contacts"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def updateOrganizationAccounts(
      orgId: Option[OrganizationId],
      info: Option[Info],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationAccountsUpdated(
      orgId,
      info,
      actingMember
    )
    log.info(
      s"updateOrganizationAccounts: orgId $orgId info $info"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  def updateOrganizationStatus(
      orgId: Option[OrganizationId],
      newStatus: OrganizationStatus,
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationStatusUpdated(
      orgId,
      newStatus,
      actingMember
    )
    log.info(
      s"updateOrganizationStatus: orgId $orgId newStatus $newStatus"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def updateParent(
      orgId: Option[OrganizationId],
      newParent: Option[OrganizationId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = ParentUpdated(
      orgId,
      newParent,
      actingMember
    )
    log.info(
      s"updateParent: orgId $orgId newParent $newParent"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def terminateOrganization(
      orgId: Option[OrganizationId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationTerminated(
      orgId,
      actingMember
    )
    log.info(
      s"terminateOrganization: orgId $orgId"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def suspendOrganization(
      orgId: Option[OrganizationId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationSuspended(
      orgId,
      actingMember
    )
    log.info(
      s"suspendOrganization: orgId $orgId"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def activateOrganization(
      orgId: Option[OrganizationId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationActivated(
      orgId,
      actingMember
    )
    log.info(
      s"activateOrganization: orgId $orgId"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def releaseOrganization(
      orgId: Option[OrganizationId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationReleased(
      orgId,
      actingMember
    )
    log.info(
      s"releaseOrganization: orgId $orgId"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def editOrganizationInfo(
      orgId: Option[OrganizationId],
      info: Info,
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    OrganizationValidation.validateInfo(info) match {
      case Validated.Valid(info) => {
        val event = OrganizationInfoUpdated(
          orgId,
          Some(info),
          actingMember
        )
        log.info(s"editOrganizationInfo: event $event")
        Effect.persist(event).thenReply(replyTo) { _ =>
          StatusReply.Success(OrganizationEventResponse(event))
        }
      }
      case Validated.Invalid(errors) => {
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"Invalid Organization Info with errors: ${errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(",")}"
          )
        }
      }
    }
  }

  private def removeOwnersFromOrganization(
      orgId: Option[OrganizationId],
      removedOwners: Seq[MemberId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OwnersRemovedFromOrganization(
      orgId,
      removedOwners,
      actingMember
    )
    log.info(
      s"removeOwnersFromOrganization: orgId $orgId removedOwners $removedOwners"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def addOwnersToOrganization(
      orgId: Option[OrganizationId],
      newOwners: Seq[MemberId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OwnersAddedToOrganization(
      orgId,
      newOwners,
      actingMember
    )
    log.info(s"addOwnersToOrganization: orgId $orgId newMembers $newOwners")
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def removeMembersFromOrganization(
      orgId: Option[OrganizationId],
      removedMembers: Seq[MemberId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = MembersRemovedFromOrganization(
      orgId,
      removedMembers,
      actingMember
    )
    log.info(
      s"removeMembersFromOrganization: orgId $orgId removedMembers $removedMembers"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def addMembersToOrganization(
      orgId: Option[OrganizationId],
      newMembers: Seq[MemberId],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = MembersAddedToOrganization(
      orgId,
      newMembers,
      actingMember
    )
    log.info(s"addMembersToOrganization: orgId $orgId newMembers $newMembers")
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def getOrganizationInfo(
      orgId: Option[OrganizationId],
      state: OrganizationState,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    log.info(s"getOrganizationInfo: orgId ${orgId.map(_.id)}")
    Effect.reply(replyTo) {
      StatusReply.Success(
        OrganizationInfo(orgId, state.info)
      )
    }
  }

  private def establishOrganization(
      orgId: Option[OrganizationId],
      info: Info,
      parent: Option[OrganizationId],
      members: Seq[MemberId],
      owners: Seq[MemberId],
      contacts: Seq[Contacts],
      actingMember: Option[MemberId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    OrganizationValidation.validateInfo(info) match {
      case Validated.Valid(info) => {
        val event = OrganizationEstablished(
          orgId,
          Some(info),
          parent,
          members,
          owners,
          contacts,
          actingMember
        )
        log.info(s"establishOrganization: event $event")
        Effect.persist(event).thenReply(replyTo) { _ =>
          StatusReply.Success(OrganizationEventResponse(event))
        }
      }
      case Validated.Invalid(errors) => {
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"Invalid Organization Info with errors: ${errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(",")}"
          )
        }
      }
    }
  }

  private def getOrganizationById(
      orgId: Option[OrganizationId],
      state: OrganizationState,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    log.info(s"getOrganizationById: orgId ${orgId.map(_.id)}")
    Effect.reply(replyTo) {
      StatusReply.Success(
        com.improving.app.organization.Organization(
          orgId,
          state.info,
          state.parent,
          state.members,
          state.owners,
          state.contacts,
          state.meta,
          state.status
        )
      )
    }
  }

  private val commandHandler: (
      OrganizationState,
      OrganizationCommand
  ) => ReplyEffect[OrganizationEvent, OrganizationState] = { (state, command: OrganizationCommand) =>
    {
      command.request match {
        case cmd if !isCommandValidForState(state, cmd) => {
          Effect.reply(command.replyTo) {
            StatusReply.Error(
              s"Invalid Command ${command.request} for State $state"
            )
          }
        }
        case UpdateOrganizationContactsRequest(
              orgId,
              contacts,
              actingMember,
              _
            ) =>
          updateOrganizationContacts(
            orgId,
            contacts,
            actingMember,
            command.replyTo
          )
        case UpdateOrganizationAccountsRequest(
              orgId,
              info,
              actingMember,
              _
            ) =>
          updateOrganizationAccounts(
            orgId,
            info,
            actingMember,
            command.replyTo
          )
        case UpdateOrganizationStatusRequest(
              orgId,
              newStatus,
              actingMember,
              _
            ) =>
          updateOrganizationStatus(
            orgId,
            newStatus,
            actingMember,
            command.replyTo
          )
        case UpdateParentRequest(orgId, newParent, actingMember, _) =>
          updateParent(orgId, newParent, actingMember, command.replyTo)
        case TerminateOrganizationRequest(orgId, actingMember, _) =>
          terminateOrganization(orgId, actingMember, command.replyTo)
        case SuspendOrganizationRequest(orgId, actingMember, _) =>
          suspendOrganization(orgId, actingMember, command.replyTo)
        case ActivateOrganizationRequest(orgId, actingMember, _) =>
          activateOrganization(orgId, actingMember, command.replyTo)
        case ReleaseOrganizationRequest(orgId, actingMember, _) =>
          releaseOrganization(orgId, actingMember, command.replyTo)
        case EditOrganizationInfoRequest(
              orgId,
              Some(info),
              actingMember,
              _
            ) =>
          editOrganizationInfo(
            orgId,
            info,
            actingMember,
            command.replyTo
          )
        case RemoveOwnersFromOrganizationRequest(
              orgId,
              removedOwners,
              actingMember,
              _
            ) =>
          removeOwnersFromOrganization(
            orgId,
            removedOwners,
            actingMember,
            command.replyTo
          )
        case AddOwnersToOrganizationRequest(
              orgId,
              newOwners,
              actingMember,
              _
            ) =>
          addOwnersToOrganization(
            orgId,
            newOwners,
            actingMember,
            command.replyTo
          )
        case RemoveMembersFromOrganizationRequest(
              orgId,
              removedMembers,
              actingMember,
              _
            ) =>
          removeMembersFromOrganization(
            orgId,
            removedMembers,
            actingMember,
            command.replyTo
          )
        case AddMembersToOrganizationRequest(
              orgId,
              newMembers,
              actingMember,
              _
            ) =>
          addMembersToOrganization(
            orgId,
            newMembers,
            actingMember,
            command.replyTo
          )
        case GetOrganizationInfoRequest(orgId, _) =>
          getOrganizationInfo(orgId, state, command.replyTo)
        case EstablishOrganizationRequest(
              Some(info),
              parent,
              members,
              owners,
              contacts,
              actingMember,
              _
            ) =>
          log.info(
            s"EstablishOrganizationRequest: $info $parent $members $owners $contacts $actingMember"
          )
          establishOrganization(
            state.orgId,
            info,
            parent,
            members,
            owners,
            contacts,
            actingMember,
            command.replyTo
          )
        case GetOrganizationByIdRequest(orgId, _) =>
          getOrganizationById(orgId, state, command.replyTo)
        case other =>
          throw new RuntimeException(s"Invalid/Unhandled request $other")
      }
    }
  }

  private val eventHandler: (OrganizationState, OrganizationEvent) => OrganizationState = { (state, event) =>
    {
      event match {
        case OrganizationEstablished(
              _,
              info,
              parent,
              members,
              owners,
              contacts,
              actingMember,
              _
            ) =>
          state.copy(
            info = info,
            parent = parent,
            members = members,
            owners = owners,
            contacts = contacts,
            meta = Some(createMetaInfo(actingMember)),
            status = OrganizationStatus.ORGANIZATION_STATUS_DRAFT
          )
        case MembersAddedToOrganization(
              _,
              newMembers,
              actingMember,
              _
            ) =>
          state.copy(
            members = state.members ++ newMembers,
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case MembersRemovedFromOrganization(
              _,
              removedMembers,
              actingMember,
              _
            ) =>
          state.copy(
            members = state.members.filterNot(member => removedMembers.contains(member)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OwnersAddedToOrganization(
              _,
              newOwners,
              actingMember,
              _
            ) =>
          state.copy(
            owners = state.owners ++ newOwners,
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OwnersRemovedFromOrganization(
              _,
              removedOwners,
              actingMember,
              _
            ) =>
          state.copy(
            owners = state.owners.filterNot(member => removedOwners.contains(member)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OrganizationInfoUpdated(_, Some(newInfo), actingMember, _) =>
          state.copy(
            info = state.info.map(updateInfo(_, newInfo)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OrganizationReleased(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_RELEASED,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
              )
            )
          )
        case OrganizationActivated(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_ACTIVE)
              )
            )
          )
        case OrganizationSuspended(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED)
              )
            )
          )
        case OrganizationTerminated(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_TERMINATED,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_TERMINATED)
              )
            )
          )
        case ParentUpdated(_, newParent, actingMember, _) =>
          state.copy(
            parent = newParent,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember
              )
            )
          )
        case OrganizationStatusUpdated(
              _,
              newStatus,
              actingMember,
              _
            ) =>
          state.copy(
            status = newStatus,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(newStatus)
              )
            )
          )
        case OrganizationContactsUpdated(
              _,
              contacts,
              actingMember,
              _
            ) =>
          state.copy(
            contacts = contacts,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember
              )
            )
          )
        case OrganizationAccountsUpdated(
              _,
              Some(newInfo),
              actingMember,
              _
            ) =>
          state.copy(
            info = state.info.map(updateInfo(_, newInfo)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case other =>
          throw new RuntimeException(s"Invalid/Unhandled event $other")
      }
    }
  }
}
