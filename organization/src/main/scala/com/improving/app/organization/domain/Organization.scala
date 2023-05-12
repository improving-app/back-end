package com.improving.app.organization.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.MemberId
import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors._
import com.improving.app.organization.domain.OrganizationState.ORGANIZATION_STATE_DRAFT

import java.time.Instant

object Organization {
  val TypeKey: EntityTypeKey[OrganizationRequestEnvelope] = EntityTypeKey[OrganizationRequestEnvelope]("Organization")

  case class OrganizationRequestEnvelope(
      request: OrganizationRequestPB,
      replyTo: ActorRef[StatusReply[OrganizationEvent]]
  )

  sealed trait OrganizationState

  private case object UninitializedState extends OrganizationState

  sealed private trait EstablishedState extends OrganizationState {
    val info: OrganizationInfo
    val metaInfo: OrganizationMetaInfo
    val members: Set[MemberId]
    val owners: Set[MemberId]
  }

  sealed private trait InactiveState extends EstablishedState

  private case class DraftState(
                                 info: OrganizationInfo,
                                 metaInfo: OrganizationMetaInfo,
                                 members: Set[MemberId],
                                 owners: Set[MemberId]
                               ) extends InactiveState

  private case class ActiveState(
                                  info: OrganizationInfo,
                                  metaInfo: OrganizationMetaInfo,
                                  members: Set[MemberId],
                                  owners: Set[MemberId]
                                ) extends EstablishedState

  private case class SuspendedState(
                                     info: OrganizationInfo,
                                     metaInfo: OrganizationMetaInfo,
                                     members: Set[MemberId],
                                     owners: Set[MemberId]
                                   ) extends InactiveState

  def apply(persistenceId: PersistenceId): Behavior[OrganizationRequestEnvelope] = {
    Behaviors.setup(context =>
      EventSourcedBehavior[OrganizationRequestEnvelope, OrganizationEvent, OrganizationState](
        persistenceId = persistenceId,
        emptyState = UninitializedState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    )
  }

  private val commandHandler: (OrganizationState, OrganizationRequestEnvelope) => ReplyEffect[OrganizationEvent, OrganizationState] = { (state, envelope) =>
    val result: Either[Error, OrganizationEvent] = state match {
      case UninitializedState =>
        envelope.request match {
          case command: EstablishOrganization => establishOrganization(command)
          case _ => Left(StateError("Organization is not established"))
        }
      case draftState: DraftState =>
        envelope.request match {
          case command: ActivateOrganization => activateOrganization(draftState, command)
          case command: SuspendOrganization => suspendOrganization(draftState, command)
          case command: TerminateOrganization => terminateOrganization(draftState, command)
          case command: EditOrganizationInfo => editOrganizationInfo(draftState, command)
          case command: AddMembersToOrganization => addMembersToOrganization(draftState, command)
          case command: RemoveMembersFromOrganization => removeMembersFromOrganization(draftState, command)
          case command: AddOwnersToOrganization => addOwnersToOrganization(draftState, command)
          case command: RemoveOwnersFromOrganization => removeOwnersFromOrganization(draftState, command)
          case _ => Left(StateError("Message type not supported in draft state"))
        }
      case activeState: ActiveState =>
        envelope.request match {
          case command: SuspendOrganization => suspendOrganization(activeState, command)
          case command: TerminateOrganization => terminateOrganization(activeState, command)
          case command: EditOrganizationInfo => editOrganizationInfo(activeState, command)
          case command: AddMembersToOrganization => addMembersToOrganization(activeState, command)
          case command: RemoveMembersFromOrganization => removeMembersFromOrganization(activeState, command)
          case command: AddOwnersToOrganization => addOwnersToOrganization(activeState, command)
          case command: RemoveOwnersFromOrganization => removeOwnersFromOrganization(activeState, command)
          case _ => Left(StateError("Message type not supported in active state"))
        }
      case suspendedState: SuspendedState =>
        envelope.request match {
          case command: ActivateOrganization => activateOrganization(suspendedState, command)
          case command: TerminateOrganization => terminateOrganization(suspendedState, command)
          case command: EditOrganizationInfo => editOrganizationInfo(suspendedState, command)
          case command: AddMembersToOrganization => addMembersToOrganization(suspendedState, command)
          case command: RemoveMembersFromOrganization => removeMembersFromOrganization(suspendedState, command)
          case command: AddOwnersToOrganization => addOwnersToOrganization(suspendedState, command)
          case command: RemoveOwnersFromOrganization => removeOwnersFromOrganization(suspendedState, command)
          case _ => Left(StateError("Message type not supported in suspended state"))
        }
    }
    result match {
      case Left(error) => Effect.reply(envelope.replyTo)(StatusReply.Error(error.message))
      case Right(event) => Effect.persist(event).thenReply(envelope.replyTo) { _ => StatusReply.Success(event) }
    }
  }

  private val eventHandler: (OrganizationState, OrganizationEvent) => OrganizationState = { (state, event) =>
    event match {
      case OrganizationEvent.Empty => state
      case event: OrganizationEstablished =>
        state match {
          case UninitializedState => DraftState(info = event.organizationInfo.get, metaInfo = event.metaInfo.get, Set.empty, Set.empty)
          case _: DraftState => state
          case _: ActiveState => state
          case _: SuspendedState => state
        }
      case event: OrganizationActivated =>
        state match {
          case x: DraftState => ActiveState(x.info, event.metaInfo.get, x.members, x.owners)
          case _: ActiveState => state
          case x: SuspendedState => ActiveState(x.info, event.metaInfo.get, x.members, x.owners)
          case UninitializedState => UninitializedState
        }
      case event: OrganizationSuspended =>
        state match {
          case x: DraftState => SuspendedState(x.info, event.metaInfo.get, x.members, x.owners)
          case x: ActiveState => SuspendedState(x.info, event.metaInfo.get, x.members, x.owners)
          case x: SuspendedState => SuspendedState(x.info, event.metaInfo.get, x.members, x.owners)
          case UninitializedState => UninitializedState
        }
      case _: OrganizationTerminated => state
      case event: OrganizationInfoEdited =>
        state match {
          case x: DraftState => DraftState(event.getNewInfo, event.getMetaInfo, x.members, x.owners)
          case x: ActiveState => ActiveState(event.getNewInfo, event.getMetaInfo, x.members, x.owners)
          case x: SuspendedState => SuspendedState(event.getNewInfo, event.getMetaInfo, x.members, x.owners)
          case UninitializedState => UninitializedState
        }
      case event: MembersAddedToOrganization =>
        state match {
          case x: DraftState => DraftState(x.info, event.getMetaInfo, x.members ++ event.membersAdded, x.owners)
          case x: ActiveState => ActiveState(x.info, event.getMetaInfo, x.members ++ event.membersAdded, x.owners)
          case x: SuspendedState => SuspendedState(x.info, event.getMetaInfo, x.members ++ event.membersAdded, x.owners)
          case UninitializedState => UninitializedState
        }
      case event: MembersRemovedFromOrganization =>
        state match {
          case x: DraftState => DraftState(x.info, event.getMetaInfo, x.members -- event.membersRemoved, x.owners)
          case x: ActiveState => ActiveState(x.info, event.getMetaInfo, x.members -- event.membersRemoved, x.owners)
          case x: SuspendedState => SuspendedState(x.info, event.getMetaInfo, x.members -- event.membersRemoved, x.owners)
          case UninitializedState => UninitializedState
        }
      case event: OwnersAddedToOrganization =>
        state match {
          case x: DraftState => DraftState(x.info, event.getMetaInfo, x.members, x.owners ++ event.ownersAdded)
          case x: ActiveState => ActiveState(x.info, event.getMetaInfo, x.members, x.owners ++ event.ownersAdded)
          case x: SuspendedState => SuspendedState(x.info, event.getMetaInfo, x.members, x.owners ++ event.ownersAdded)
          case UninitializedState => UninitializedState
        }
      case event: OwnersRemovedFromOrganization =>
        state match {
          case x: DraftState => DraftState(x.info, event.getMetaInfo, x.members, x.owners -- event.ownersRemoved)
          case x: ActiveState => ActiveState(x.info, event.getMetaInfo, x.members, x.owners -- event.ownersRemoved)
          case x: SuspendedState => SuspendedState(x.info, event.getMetaInfo, x.members, x.owners -- event.ownersRemoved)
          case UninitializedState => UninitializedState
        }
    }
  }

  private def updateMetaInfo(
      metaInfo: OrganizationMetaInfo,
      lastUpdatedByOpt: MemberId
  ): OrganizationMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Timestamp(Instant.now()))
  }

  private def establishOrganization(establishOrganization: EstablishOrganization): Either[Error, OrganizationEvent] = {
    val organizationInfo = establishOrganization.organizationInfo

    val now = Instant.now()

    val newMetaInfo = OrganizationMetaInfo(
      createdOn = Timestamp(now),
      createdBy = establishOrganization.onBehalfOf,
      lastUpdated = Timestamp(now),
      lastUpdatedBy = establishOrganization.onBehalfOf,
      state = ORGANIZATION_STATE_DRAFT
    )

    Right(
      OrganizationEstablished(
        organizationId = establishOrganization.organizationId,
        metaInfo = newMetaInfo,
        organizationInfo = organizationInfo
      )
    )
  }

  private def activateOrganization(
      state: InactiveState,
      activateOrganization: ActivateOrganization,
  ): Either[Error, OrganizationEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = activateOrganization.onBehalfOf)
    Right(
      OrganizationActivated(
        organizationId = activateOrganization.organizationId,
        metaInfo = newMetaInfo
      )
    )
  }

  private def suspendOrganization(
      state: EstablishedState,
      suspendOrganization: SuspendOrganization,
  ): Either[Error, OrganizationEvent] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = suspendOrganization.onBehalfOf)
    Right(
      OrganizationSuspended(
        organizationId = suspendOrganization.organizationId,
        metaInfo = newMetaInfo,
      )
    )
  }

  private def terminateOrganization(
      establishedState: EstablishedState,
      terminate: TerminateOrganization
  ): Either[Error, OrganizationEvent] = {
    Right(
      OrganizationTerminated(
        organizationId = terminate.organizationId
      )
    )
  }

  private def editOrganizationInfo(
      state: EstablishedState,
      command: EditOrganizationInfo
  ): Either[Error, OrganizationInfoEdited] = {
    val fieldsToUpdate = command.organizationInfo

    var updatedInfo = state.info
    fieldsToUpdate.name.foreach(newName => updatedInfo = updatedInfo.copy(name = newName))
    fieldsToUpdate.shortName.foreach(newShortName => updatedInfo = updatedInfo.copy(shortName = Some(newShortName)))
    fieldsToUpdate.isPublic.foreach(newIsPublic => updatedInfo = updatedInfo.copy(isPublic = newIsPublic))
    fieldsToUpdate.address.foreach(newAddress => updatedInfo = updatedInfo.copy(address = Some(newAddress)))
    fieldsToUpdate.url.foreach(newUrl => updatedInfo = updatedInfo.copy(url = Some(newUrl)))
    fieldsToUpdate.logo.foreach(newLogo => updatedInfo = updatedInfo.copy(logo = Some(newLogo)))

    val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

    Right(
      OrganizationInfoEdited(
        organizationId = command.organizationId,
        metaInfo = updatedMetaInfo,
        oldInfo = state.info,
        newInfo = updatedInfo
      )
    )
  }

  def addMembersToOrganization(
                                state: EstablishedState,
                                command: AddMembersToOrganization
                              ): Either[Error, MembersAddedToOrganization] = {
    val maybeValidationError = applyAllValidators[AddMembersToOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf),
      c => nonEmpty("members to add")(c.membersToAdd),
      c => validateAll(memberIdValidator)(c.membersToAdd)
    )(command)
    if(maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
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
  }

  def removeMembersFromOrganization(
                                     state: EstablishedState,
                                     command: RemoveMembersFromOrganization
                                   ): Either[Error, MembersRemovedFromOrganization] = {
    val maybeValidationError = applyAllValidators[RemoveMembersFromOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf),
      c => nonEmpty("members to remove")(c.membersToRemove),
      c => validateAll(memberIdValidator)(c.membersToRemove)
    )(command)
    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
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
  }

  def addOwnersToOrganization(
                               state: EstablishedState,
                               command: AddOwnersToOrganization
                             ): Either[Error, OwnersAddedToOrganization] = {
    val maybeValidationError = applyAllValidators[AddOwnersToOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf),
      c => nonEmpty("owners to add")(c.ownersToAdd),
      c => validateAll(memberIdValidator)(c.ownersToAdd)
    )(command)
    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
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
  }

  def removeOwnersFromOrganization(
                                    state: EstablishedState,
                                    command: RemoveOwnersFromOrganization
                                  ): Either[Error, OwnersRemovedFromOrganization] = {
    val maybeValidationError = applyAllValidators[RemoveOwnersFromOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf),
      c => nonEmpty("members to remove")(c.ownersToRemove),
      c => validateAll(memberIdValidator)(c.ownersToRemove)
    )(command)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
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
  }
}
