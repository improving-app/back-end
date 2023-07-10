package com.improving.app.organization.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{Contact, MemberId}
import com.improving.app.common.errors._
import com.improving.app.organization.domain.OrganizationState._
import com.improving.app.organization.domain.Validation.{
  draftTransitionOrganizationInfoValidator,
  organizationCommandValidator,
  organizationQueryValidator
}
import com.improving.app.organization.domain.util.{EditableOrganizationInfoUtil, OrganizationInfoUtil}

import java.time.{Clock, Instant}

object Organization {
  val TypeKey: EntityTypeKey[OrganizationRequestEnvelope] = EntityTypeKey[OrganizationRequestEnvelope]("Organization")

  private val clock: Clock = Clock.systemDefaultZone()

  case class OrganizationRequestEnvelope(
      request: OrganizationRequestPB,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  )

  sealed trait OrganizationState

  private case object UninitializedState extends OrganizationState

  sealed private trait EstablishedState extends OrganizationState {
    val metaInfo: OrganizationMetaInfo
    val members: Set[MemberId]
    val owners: Set[MemberId]
    val contacts: Set[Contact]
  }

  sealed private trait DefinedState extends EstablishedState {
    val info: OrganizationInfo
  }

  sealed private trait InactiveState extends EstablishedState

  private case class DraftState(
      info: EditableOrganizationInfo,
      metaInfo: OrganizationMetaInfo,
      members: Set[MemberId],
      owners: Set[MemberId],
      contacts: Set[Contact],
  ) extends InactiveState

  private case class ActiveState(
      info: OrganizationInfo,
      metaInfo: OrganizationMetaInfo,
      members: Set[MemberId],
      owners: Set[MemberId],
      contacts: Set[Contact],
  ) extends DefinedState

  private case class SuspendedState(
      info: OrganizationInfo,
      metaInfo: OrganizationMetaInfo,
      members: Set[MemberId],
      owners: Set[MemberId],
      contacts: Set[Contact],
  ) extends DefinedState
      with InactiveState

  def apply(persistenceId: PersistenceId): Behavior[OrganizationRequestEnvelope] = {
    Behaviors.setup(_ =>
      EventSourcedBehavior[OrganizationRequestEnvelope, OrganizationEventPB, OrganizationState](
        persistenceId = persistenceId,
        emptyState = UninitializedState,
        commandHandler = requestHandler,
        eventHandler = eventHandler
      )
    )
  }

  private val requestHandler
      : (OrganizationState, OrganizationRequestEnvelope) => ReplyEffect[OrganizationEventPB, OrganizationState] =
    (state, envelope) =>
      envelope.request match {
        case c: OrganizationCommand =>
          organizationCommandValidator(c) match {
            case None =>
              handleCommand(state, c) match {
                case Left(error) => Effect.reply(envelope.replyTo)(StatusReply.Error(error.message))
                case Right(event) =>
                  Effect
                    .persist(event)
                    .thenReply(envelope.replyTo)((_: OrganizationState) => StatusReply.Success(event))
                    .asInstanceOf[ReplyEffect[OrganizationEventPB, OrganizationState]]
              }
            case Some(errors) => Effect.reply(envelope.replyTo)(StatusReply.Error(errors.message))
          }
        case query: OrganizationQuery =>
          organizationQueryValidator(query) match {
            case None =>
              handleQuery(state, query) match {
                case Left(error)   => Effect.reply(envelope.replyTo)(StatusReply.Error(error.message))
                case Right(result) => Effect.reply(envelope.replyTo)(StatusReply.Success(result))
              }
            case Some(errors) => Effect.reply(envelope.replyTo)(StatusReply.Error(errors.message))

          }
        case OrganizationRequestPB.Empty =>
          Effect.reply(envelope.replyTo)(StatusReply.Error("OrganizationRequest is of type Empty"))
      }

  private val eventHandler: (OrganizationState, OrganizationEventPB) => OrganizationState = (state, event) =>
    event match {
      case OrganizationEventPB.Empty => state
      case event: OrganizationEstablished =>
        state match {
          case UninitializedState =>
            DraftState(info = event.getOrganizationInfo, metaInfo = event.getMetaInfo, Set.empty, Set.empty, Set.empty)
          case _: DraftState     => state
          case _: ActiveState    => state
          case _: SuspendedState => state
        }
      case event: OrganizationActivated =>
        state match {
          case x: DraftState =>
            ActiveState(x.info.toInfo, event.getMetaInfo, x.members, x.owners, x.contacts)
          case _: ActiveState     => state
          case x: SuspendedState  => ActiveState(x.info, event.getMetaInfo, x.members, x.owners, x.contacts)
          case UninitializedState => UninitializedState
        }
      case event: OrganizationSuspended =>
        state match {
          case x: DraftState =>
            SuspendedState(x.info.toInfo, event.getMetaInfo, x.members, x.owners, x.contacts)
          case x: ActiveState     => SuspendedState(x.info, event.getMetaInfo, x.members, x.owners, x.contacts)
          case x: SuspendedState  => SuspendedState(x.info, event.getMetaInfo, x.members, x.owners, x.contacts)
          case UninitializedState => UninitializedState
        }
      case _: OrganizationTerminated => state
      case event: OrganizationInfoEdited =>
        state match {
          case x: DraftState => x.copy(metaInfo = event.getMetaInfo, info = event.getNewInfo)
          case x: ActiveState =>
            x.copy(metaInfo = event.getMetaInfo, info = event.getNewInfo.toInfo)
          case x: SuspendedState =>
            x.copy(metaInfo = event.getMetaInfo, info = event.getNewInfo.toInfo)
          case UninitializedState => UninitializedState
        }
      case event: MembersAddedToOrganization =>
        state match {
          case x: DraftState      => x.copy(metaInfo = event.getMetaInfo, members = x.members ++ event.membersAdded)
          case x: ActiveState     => x.copy(metaInfo = event.getMetaInfo, members = x.members ++ event.membersAdded)
          case x: SuspendedState  => x.copy(metaInfo = event.getMetaInfo, members = x.members ++ event.membersAdded)
          case UninitializedState => UninitializedState
        }
      case event: MembersRemovedFromOrganization =>
        state match {
          case x: DraftState      => x.copy(metaInfo = event.getMetaInfo, members = x.members -- event.membersRemoved)
          case x: ActiveState     => x.copy(metaInfo = event.getMetaInfo, members = x.members -- event.membersRemoved)
          case x: SuspendedState  => x.copy(metaInfo = event.getMetaInfo, members = x.members -- event.membersRemoved)
          case UninitializedState => UninitializedState
        }
      case event: OwnersAddedToOrganization =>
        state match {
          case x: DraftState      => x.copy(metaInfo = event.getMetaInfo, owners = x.owners ++ event.ownersAdded)
          case x: ActiveState     => x.copy(metaInfo = event.getMetaInfo, owners = x.owners ++ event.ownersAdded)
          case x: SuspendedState  => x.copy(metaInfo = event.getMetaInfo, owners = x.owners ++ event.ownersAdded)
          case UninitializedState => UninitializedState
        }
      case event: OwnersRemovedFromOrganization =>
        state match {
          case x: DraftState      => x.copy(metaInfo = event.getMetaInfo, owners = x.owners -- event.ownersRemoved)
          case x: ActiveState     => x.copy(metaInfo = event.getMetaInfo, owners = x.owners -- event.ownersRemoved)
          case x: SuspendedState  => x.copy(metaInfo = event.getMetaInfo, owners = x.owners -- event.ownersRemoved)
          case UninitializedState => UninitializedState
        }
      case event: OrganizationContactsUpdated =>
        state match {
          case x: DraftState      => x.copy(metaInfo = event.getMetaInfo, contacts = event.contacts.toSet)
          case x: ActiveState     => x.copy(metaInfo = event.getMetaInfo, contacts = event.contacts.toSet)
          case x: SuspendedState  => x.copy(metaInfo = event.getMetaInfo, contacts = event.contacts.toSet)
          case UninitializedState => UninitializedState
        }
    }

  private def handleCommand(state: OrganizationState, command: OrganizationCommand): Either[Error, OrganizationEvent] =
    state match {
      case UninitializedState =>
        command match {
          case command: EstablishOrganization => establishOrganization(command)
          case _                              => Left(StateError("Organization is not established"))
        }
      case draftState: DraftState =>
        command match {
          case command: ActivateOrganization =>
            activateOrganization(Left(draftState.info), draftState.metaInfo, command)
          case command: SuspendOrganization   => suspendOrganization(draftState, command)
          case command: TerminateOrganization => terminateOrganization(command)
          case command: EditOrganizationInfo =>
            editOrganizationInfo(Left(draftState.info), draftState.metaInfo, command)
          case command: AddMembersToOrganization      => addMembersToOrganization(draftState, command)
          case command: RemoveMembersFromOrganization => removeMembersFromOrganization(draftState, command)
          case command: AddOwnersToOrganization       => addOwnersToOrganization(draftState, command)
          case command: RemoveOwnersFromOrganization  => removeOwnersFromOrganization(draftState, command)
          case command: UpdateOrganizationContacts    => updateOrganizationContacts(draftState, command)
          case _                                      => Left(StateError("Message type not supported in draft state"))
        }
      case activeState: ActiveState =>
        command match {
          case command: SuspendOrganization   => suspendOrganization(activeState, command)
          case command: TerminateOrganization => terminateOrganization(command)
          case command: EditOrganizationInfo =>
            editOrganizationInfo(Right(activeState.info), activeState.metaInfo, command)
          case command: AddMembersToOrganization      => addMembersToOrganization(activeState, command)
          case command: RemoveMembersFromOrganization => removeMembersFromOrganization(activeState, command)
          case command: AddOwnersToOrganization       => addOwnersToOrganization(activeState, command)
          case command: RemoveOwnersFromOrganization  => removeOwnersFromOrganization(activeState, command)
          case command: UpdateOrganizationContacts    => updateOrganizationContacts(activeState, command)
          case _                                      => Left(StateError("Message type not supported in active state"))
        }
      case suspendedState: SuspendedState =>
        command match {
          case command: ActivateOrganization =>
            activateOrganization(Right(suspendedState.info), suspendedState.metaInfo, command)
          case command: TerminateOrganization => terminateOrganization(command)
          case command: EditOrganizationInfo =>
            editOrganizationInfo(Right(suspendedState.info), suspendedState.metaInfo, command)
          case command: AddMembersToOrganization      => addMembersToOrganization(suspendedState, command)
          case command: RemoveMembersFromOrganization => removeMembersFromOrganization(suspendedState, command)
          case command: AddOwnersToOrganization       => addOwnersToOrganization(suspendedState, command)
          case command: RemoveOwnersFromOrganization  => removeOwnersFromOrganization(suspendedState, command)
          case command: UpdateOrganizationContacts    => updateOrganizationContacts(suspendedState, command)
          case _ => Left(StateError("Message type not supported in suspended state"))
        }
    }

  private def handleQuery(
      state: OrganizationState,
      query: OrganizationQuery
  ): Either[Error, OrganizationQueryResponse] = {
    state match {
      case state: DefinedState =>
        query match {
          case GetOrganizationInfo(organizationId, _, _) =>
            Right(OrganizationInfoResponse(organizationId, Some(state.info)))
          case GetOrganizationContacts(organizationId, _, _) =>
            Right(OrganizationContactsResponse(organizationId, contacts = state.contacts.toSeq))
        }
      case _ => Left(StateError("Organization is not established"))
    }
  }

  private def updateMetaInfo(
      metaInfo: OrganizationMetaInfo,
      lastUpdatedByOpt: Option[MemberId]
  ): OrganizationMetaInfo =
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now(clock))))

  private def establishOrganization(establishOrganization: EstablishOrganization): Either[Error, OrganizationEvent] = {
    val organizationInfo = establishOrganization.organizationInfo

    val now = Instant.now(clock)

    val newMetaInfo = OrganizationMetaInfo(
      createdOn = Some(Timestamp(now)),
      createdBy = establishOrganization.onBehalfOf,
      lastUpdated = Some(Timestamp(now)),
      lastUpdatedBy = establishOrganization.onBehalfOf,
      state = ORGANIZATION_STATE_DRAFT
    )

    Right(
      OrganizationEstablished(
        organizationId = establishOrganization.organizationId,
        metaInfo = Some(newMetaInfo),
        organizationInfo = organizationInfo
      )
    )
  }

  private def activateOrganization(
      info: Either[EditableOrganizationInfo, OrganizationInfo],
      meta: OrganizationMetaInfo,
      activateOrganization: ActivateOrganization,
  ): Either[Error, OrganizationEvent] = {
    val newMeta = updateMetaInfo(metaInfo = meta, lastUpdatedByOpt = activateOrganization.onBehalfOf)
    info match {
      case Left(e: EditableOrganizationInfo) =>
        val validationErrorsOpt = draftTransitionOrganizationInfoValidator(e)
        if (validationErrorsOpt.isEmpty) {
          Right(
            OrganizationActivated(
              organizationId = activateOrganization.organizationId,
              metaInfo = Some(newMeta)
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(_: OrganizationInfo) =>
        Right(
          OrganizationActivated(
            activateOrganization.organizationId,
            Some(newMeta)
          )
        )
    }
  }

  private def suspendOrganization(
      state: EstablishedState,
      suspendOrganization: SuspendOrganization,
  ): Either[Error, OrganizationEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = suspendOrganization.onBehalfOf)
    Right(
      OrganizationSuspended(
        organizationId = suspendOrganization.organizationId,
        metaInfo = Some(newMetaInfo),
      )
    )
  }

  private def terminateOrganization(
      terminate: TerminateOrganization
  ): Either[Error, OrganizationEvent] = {
    Right(
      OrganizationTerminated(
        organizationId = terminate.organizationId
      )
    )
  }

  private def editOrganizationInfo(
      info: Either[EditableOrganizationInfo, OrganizationInfo],
      meta: OrganizationMetaInfo,
      command: EditOrganizationInfo
  ): Either[Error, OrganizationInfoEdited] = {
    val newMeta = meta.copy(
      lastUpdatedBy = command.onBehalfOf,
      lastUpdated = Some(Timestamp(Instant.now(clock)))
    )

    command.organizationInfo
      .map { editable =>
        val event = OrganizationInfoEdited(
          command.organizationId,
          Some(info match {
            case Right(i: OrganizationInfo)        => i.updateInfo(editable).toEditable
            case Left(e: EditableOrganizationInfo) => e.updateInfo(editable)
          }),
          Some(newMeta)
        )
        Right(event)
      }
      .getOrElse(
        Right(
          OrganizationInfoEdited(
            command.organizationId,
            Some(info match {
              case Right(i: OrganizationInfo)        => i.toEditable
              case Left(e: EditableOrganizationInfo) => e
            }),
            Some(newMeta)
          )
        )
      )
  }

  def addMembersToOrganization(
      state: EstablishedState,
      command: AddMembersToOrganization
  ): Either[Error, MembersAddedToOrganization] = {
    val newMembersAdded: Set[MemberId] = command.membersToAdd.toSet -- state.members
    val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

    Right(
      MembersAddedToOrganization(
        organizationId = command.organizationId,
        metaInfo = Some(updatedMetaInfo),
        membersAdded = newMembersAdded.toSeq
      )
    )
  }

  def removeMembersFromOrganization(
      state: EstablishedState,
      command: RemoveMembersFromOrganization
  ): Either[Error, MembersRemovedFromOrganization] = {
    val realMembersToRemove = state.members.intersect(command.membersToRemove.toSet)
    val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

    Right(
      MembersRemovedFromOrganization(
        organizationId = command.organizationId,
        metaInfo = Some(updatedMetaInfo),
        membersRemoved = realMembersToRemove.toSeq
      )
    )
  }

  def addOwnersToOrganization(
      state: EstablishedState,
      command: AddOwnersToOrganization
  ): Either[Error, OwnersAddedToOrganization] = {
    val newOwnersAdded: Set[MemberId] = command.ownersToAdd.toSet -- state.owners
    val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

    Right(
      OwnersAddedToOrganization(
        organizationId = command.organizationId,
        metaInfo = Some(updatedMetaInfo),
        ownersAdded = newOwnersAdded.toSeq
      )
    )
  }

  def removeOwnersFromOrganization(
      state: EstablishedState,
      command: RemoveOwnersFromOrganization
  ): Either[Error, OwnersRemovedFromOrganization] = {
    val realOwnersToRemove = state.owners.intersect(command.ownersToRemove.toSet)
    val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

    Right(
      OwnersRemovedFromOrganization(
        organizationId = command.organizationId,
        metaInfo = Some(updatedMetaInfo),
        ownersRemoved = realOwnersToRemove.toSeq
      )
    )
  }

  def updateOrganizationContacts(
      state: EstablishedState,
      command: UpdateOrganizationContacts
  ): Either[Error, OrganizationContactsUpdated] = {
    Right(
      OrganizationContactsUpdated(
        organizationId = command.organizationId,
        metaInfo = Some(updateMetaInfo(state.metaInfo, command.onBehalfOf)),
        contacts = command.contacts
      )
    )
  }
}
