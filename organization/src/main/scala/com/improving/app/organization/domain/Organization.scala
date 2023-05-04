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
import com.improving.app.organization.domain.OrganizationValidation._

import java.time.Instant

object Organization {
  val TypeKey = EntityTypeKey[OrganizationRequestEnvelope]("Organization")

  case class OrganizationRequestEnvelope(request: OrganizationRequestPB, replyTo: ActorRef[StatusReply[OrganizationEvent]])

  sealed trait OrganizationState

  private case object UninitializedState extends OrganizationState

  private sealed trait EstablishedState extends OrganizationState {
    val info: OrganizationInfo
    val metaInfo: OrganizationMetaInfo
  }

  private sealed trait InactiveState extends EstablishedState

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

  private val commandHandler: (OrganizationState, OrganizationRequestEnvelope) => ReplyEffect[OrganizationEvent, OrganizationState] = { (state, envelope) =>
    val result: Either[Error, OrganizationEvent] = state match {
      case UninitializedState =>
        envelope.request match {
          case command: EstablishOrganization => establishOrganization(command)
          case command: TerminateOrganization => terminateOrganization(command)
          case _ => Left(StateError("Organization is not established"))
        }
      case draftState: DraftState =>
        envelope.request match {
          case command: ActivateOrganization => activateOrganization(draftState, command)
          case command: SuspendOrganization => suspendOrganization(draftState, command)
          case command: TerminateOrganization => terminateOrganization(command)
          case command: EditOrganizationInfo => editOrganizationInfo(draftState, command)
          case _ => Left(StateError("Message type not supported in draft state"))
        }
      case activeState: ActiveState =>
        envelope.request match {
          case command: SuspendOrganization => suspendOrganization(activeState, command)
          case command: TerminateOrganization => terminateOrganization(command)
          case command: EditOrganizationInfo => editOrganizationInfo(activeState, command)
          case _ => Left(StateError("Message type not supported in active state"))
        }
      case suspendedState: SuspendedState =>
        envelope.request match {
          case command: ActivateOrganization => activateOrganization(suspendedState, command)
          case command: TerminateOrganization => terminateOrganization(command)
          case command: EditOrganizationInfo => editOrganizationInfo(suspendedState, command)
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
          case UninitializedState => DraftState(info = event.organizationInfo.get, metaInfo = event.metaInfo.get)
          case _: DraftState => state
          case _: ActiveState => state
          case _: SuspendedState => state
        }
      case event: OrganizationActivated =>
        state match {
          case x: DraftState => ActiveState(x.info, event.metaInfo.get)
          case _: ActiveState => state
          case x: SuspendedState => ActiveState(x.info, event.metaInfo.get)
          case UninitializedState => UninitializedState
        }
      case event: OrganizationSuspended =>
        state match {
          case x: DraftState => SuspendedState(x.info, event.metaInfo.get)
          case x: ActiveState => SuspendedState(x.info, event.metaInfo.get)
          case x: SuspendedState => SuspendedState(x.info, event.metaInfo.get)
          case UninitializedState => UninitializedState
        }
      case _: OrganizationTerminated =>
        ???
      case event: OrganizationInfoEdited =>
        state match {
          case _: DraftState => DraftState(event.getNewInfo, event.getMetaInfo)
          case _: ActiveState => ActiveState(event.getNewInfo, event.getMetaInfo)
          case _: SuspendedState => SuspendedState(event.getNewInfo, event.getMetaInfo)
          case UninitializedState => UninitializedState
        }
    }
  }

  private def updateMetaInfo(metaInfo: OrganizationMetaInfo, lastUpdatedByOpt: Option[MemberId]): OrganizationMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def establishOrganization(establishOrganization: EstablishOrganization): Either[Error, OrganizationEvent] = {
    val maybeValidationError = applyAllValidators[EstablishOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(establishOrganization)
    if(maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val maybeOrganizationInfoError = required("organization info", inactiveStateOrganizationInfoValidator)(establishOrganization.organizationInfo)
      if (maybeOrganizationInfoError.isDefined) {
        Left(maybeOrganizationInfoError.get)
      } else {
        val organizationInfo = establishOrganization.organizationInfo.get

        val newMetaInfo = OrganizationMetaInfo(
          createdOn = Some(Timestamp(Instant.now())),
          createdBy = Some(establishOrganization.getOnBehalfOf)
        )

        Right(OrganizationEstablished(
          organizationId = establishOrganization.organizationId,
          metaInfo = Some(newMetaInfo),
          organizationInfo = Some(organizationInfo)
        ))
      }
    }
  }

  private def activateOrganization(
                                    state: InactiveState,
                                    activateOrganization: ActivateOrganization,
                                  ): Either[Error, OrganizationEvent] = {
    val maybeValidationError: Option[ValidationError] = applyAllValidators[ActivateOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(activateOrganization).orElse(activeStateOrganizationInfoValidator(state.info))

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = activateOrganization.onBehalfOf)
      Right(OrganizationActivated(
        organizationId = activateOrganization.organizationId,
        metaInfo = Some(newMetaInfo)
      ))
    }
  }

  private def suspendOrganization(
                                   state: EstablishedState,
                                   suspendOrganization: SuspendOrganization,
                           ): Either[Error, OrganizationEvent] = {
    val maybeValidationError = applyAllValidators[SuspendOrganization](
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    )(suspendOrganization)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = suspendOrganization.onBehalfOf)
      Right(OrganizationSuspended(
        organizationId = suspendOrganization.organizationId,
        metaInfo = Some(newMetaInfo),
      ))
    }
  }

  private def terminateOrganization(organization: TerminateOrganization): Either[Error, OrganizationEvent] = {
    Right(OrganizationTerminated())
  }

  private def editOrganizationInfo(
                                    state: EstablishedState,
                                    command: EditOrganizationInfo
                                  ): Either[Error, OrganizationInfoEdited] = {
    val maybeValidationError = applyAllValidators[EditOrganizationInfo](
      command => required("organization id", organizationIdValidator)(command.organizationId),
      command => required("on behalf of", memberIdValidator)(command.onBehalfOf),
    )(command)
    if(maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val fieldsToUpdate = command.getOrganizationInfo

      var updatedInfo = state.info
      fieldsToUpdate.name.foreach(newName => updatedInfo = updatedInfo.copy(name = newName))
      fieldsToUpdate.shortName.foreach(newShortName => updatedInfo = updatedInfo.copy(shortName = newShortName))
      fieldsToUpdate.isPublic.foreach(newIsPublic => updatedInfo = updatedInfo.copy(isPublic = newIsPublic))
      fieldsToUpdate.address.foreach(newAddress => updatedInfo = updatedInfo.copy(address = Some(newAddress)))
      fieldsToUpdate.url.foreach(newUrl => updatedInfo = updatedInfo.copy(url = newUrl))
      fieldsToUpdate.logo.foreach(newLogo => updatedInfo = updatedInfo.copy(logo = newLogo))

      val updatedInfoValidator = state match {
        case _: ActiveState => activeStateOrganizationInfoValidator
        case _: InactiveState => inactiveStateOrganizationInfoValidator
      }

      val maybeNewInfoInvalid = updatedInfoValidator(updatedInfo)
      if(maybeNewInfoInvalid.isDefined) {
        Left(maybeNewInfoInvalid.get)
      } else {
        val updatedMetaInfo = updateMetaInfo(state.metaInfo, command.onBehalfOf)

        Right(OrganizationInfoEdited(
          organizationId = command.organizationId,
          metaInfo = Some(updatedMetaInfo),
          oldInfo = Some(state.info),
          newInfo = Some(updatedInfo)
        ))
      }
    }
  }
}
