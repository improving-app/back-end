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
  }

  sealed private trait InactiveState extends EstablishedState

  private case class DraftState(info: OrganizationInfo, metaInfo: OrganizationMetaInfo) extends InactiveState

  private case class ActiveState(info: OrganizationInfo, metaInfo: OrganizationMetaInfo) extends EstablishedState

  private case class SuspendedState(info: OrganizationInfo, metaInfo: OrganizationMetaInfo) extends InactiveState

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

  private val commandHandler
      : (OrganizationState, OrganizationRequestEnvelope) => ReplyEffect[OrganizationEvent, OrganizationState] = {
    (state, envelope) =>
      val result: Either[Error, OrganizationEvent] = state match {
        case UninitializedState =>
          envelope.request match {
            case command: EstablishOrganization => establishOrganization(command)
            case _                              => Left(StateError("Organization is not established"))
          }
        case draftState: DraftState =>
          envelope.request match {
            case command: ActivateOrganization  => activateOrganization(draftState, command)
            case command: SuspendOrganization   => suspendOrganization(draftState, command)
            case command: TerminateOrganization => terminateOrganization(draftState, command)
            case command: EditOrganizationInfo  => editOrganizationInfo(draftState, command)
            case _                              => Left(StateError("Message type not supported in draft state"))
          }
        case activeState: ActiveState =>
          envelope.request match {
            case command: SuspendOrganization   => suspendOrganization(activeState, command)
            case command: TerminateOrganization => terminateOrganization(activeState, command)
            case command: EditOrganizationInfo  => editOrganizationInfo(activeState, command)
            case _                              => Left(StateError("Message type not supported in active state"))
          }
        case suspendedState: SuspendedState =>
          envelope.request match {
            case command: ActivateOrganization  => activateOrganization(suspendedState, command)
            case command: TerminateOrganization => terminateOrganization(suspendedState, command)
            case command: EditOrganizationInfo  => editOrganizationInfo(suspendedState, command)
            case _                              => Left(StateError("Message type not supported in suspended state"))
          }
      }
      result match {
        case Left(error)  => Effect.reply(envelope.replyTo)(StatusReply.Error(error.message))
        case Right(event) => Effect.persist(event).thenReply(envelope.replyTo) { _ => StatusReply.Success(event) }
      }
  }

  private val eventHandler: (OrganizationState, OrganizationEvent) => OrganizationState = { (state, event) =>
    event match {
      case OrganizationEvent.Empty => state
      case event: OrganizationEstablished =>
        state match {
          case UninitializedState => DraftState(info = event.organizationInfo, metaInfo = event.metaInfo)
          case _: DraftState      => state
          case _: ActiveState     => state
          case _: SuspendedState  => state
        }
      case event: OrganizationActivated =>
        state match {
          case x: DraftState      => ActiveState(x.info, event.metaInfo)
          case _: ActiveState     => state
          case x: SuspendedState  => ActiveState(x.info, event.metaInfo)
          case UninitializedState => UninitializedState
        }
      case event: OrganizationSuspended =>
        state match {
          case x: DraftState      => SuspendedState(x.info, event.metaInfo)
          case x: ActiveState     => SuspendedState(x.info, event.metaInfo)
          case x: SuspendedState  => SuspendedState(x.info, event.metaInfo)
          case UninitializedState => UninitializedState
        }
      case _: OrganizationTerminated => state
      case event: OrganizationInfoEdited =>
        state match {
          case _: DraftState      => DraftState(event.newInfo, event.metaInfo)
          case _: ActiveState     => ActiveState(event.newInfo, event.metaInfo)
          case _: SuspendedState  => SuspendedState(event.newInfo, event.metaInfo)
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
}
