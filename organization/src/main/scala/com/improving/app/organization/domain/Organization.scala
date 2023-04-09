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
  private case class DraftState(info: OrganizationInfo, metaInfo: OrganizationMetaInfo) extends EstablishedState

  private case class ActiveState(info: OrganizationInfo, metaInfo: OrganizationMetaInfo) extends EstablishedState

  private case class SuspendedState(info: OrganizationInfo, metaInfo: OrganizationMetaInfo) extends EstablishedState

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

  private val commandHandler: (OrganizationState, OrganizationRequestEnvelope) => ReplyEffect[OrganizationEvent, OrganizationState] = { (state, command) =>
    val result: Either[Error, OrganizationEvent] = state match {
      case UninitializedState =>
        command.request.asMessage.sealedValue match {
          case OrganizationRequestPBMessage.SealedValue.EstablishOrganization(command) => establishOrganization(command)
          case OrganizationRequestPBMessage.SealedValue.TerminateOrganization(command) => terminateOrganization(command)
          case _ => Left(StateError("Organization is not established"))
        }
      case draftState: DraftState =>
        command.request.asMessage.sealedValue match {
          case OrganizationRequestPBMessage.SealedValue.ActivateOrganization(command) => activateOrganization(draftState, command)
          case OrganizationRequestPBMessage.SealedValue.SuspendOrganization(command) => suspendOrganization(draftState, command)
          case OrganizationRequestPBMessage.SealedValue.TerminateOrganization(command) => terminateOrganization(command)
          case _ => Left(StateError("Message type not supported in draft state"))
        }
      case activeState: ActiveState =>
        command.request.asMessage.sealedValue match {
          case OrganizationRequestPBMessage.SealedValue.SuspendOrganization(command) => suspendOrganization(activeState, command)
          case OrganizationRequestPBMessage.SealedValue.TerminateOrganization(command) => terminateOrganization(command)
          case _ => Left(StateError("Message type not supported in active state"))
        }
      case suspendedState: SuspendedState =>
        command.request.asMessage.sealedValue match {
          case OrganizationRequestPBMessage.SealedValue.ActivateOrganization(command) => activateOrganization(suspendedState, command)
          case OrganizationRequestPBMessage.SealedValue.TerminateOrganization(command) => terminateOrganization(command)
          case _ => Left(StateError("Message type not supported in suspended state"))
        }
    }
    result match {
      case Left(error) => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
      case Right(event) => Effect.persist(event).thenReply(command.replyTo) { _ => StatusReply.Success(event) }
    }
  }

  private val eventHandler: (OrganizationState, OrganizationEvent) => OrganizationState = { (state, event) =>
    event.asMessage.sealedValue match {
      case OrganizationEventMessage.SealedValue.Empty => state
      case OrganizationEventMessage.SealedValue.OrganizationEstablished(event) =>
        state match {
          case UninitializedState => DraftState(info = event.organizationInfo.get, metaInfo = event.metaInfo.get)
          case _: DraftState => state
          case _: ActiveState => state
          case _: SuspendedState => state
        }
      case OrganizationEventMessage.SealedValue.OrganizationActivated(event) =>
        state match {
          case x: DraftState => ActiveState(x.info, event.metaInfo.get)
          case _: ActiveState => state
          case x: SuspendedState => ActiveState(x.info, event.metaInfo.get)
          case UninitializedState => UninitializedState
        }
      case OrganizationEventMessage.SealedValue.OrganizationSuspended(event) =>
        state match {
          case x: DraftState => SuspendedState(x.info, event.metaInfo.get)
          case x: ActiveState => SuspendedState(x.info, event.metaInfo.get)
          case x: SuspendedState => SuspendedState(x.info, event.metaInfo.get)
          case UninitializedState => UninitializedState
        }
      case OrganizationEventMessage.SealedValue.OrganizationTerminated(event) =>
        ???
    }
  }

  private def updateMetaInfo(metaInfo: OrganizationMetaInfo, lastUpdatedByOpt: Option[MemberId]): OrganizationMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def establishOrganization(establishOrganization: EstablishOrganization): Either[Error, OrganizationEvent] = {
    val maybeValidationError = applyAllValidators[EstablishOrganization](Seq(
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    ))(establishOrganization)
    if(maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val maybeOrganizationInfoError = required("organization info", completeOrganizationInfoValidator)(establishOrganization.organizationInfo)
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
                                    state: DraftState,
                                    activateOrganization: ActivateOrganization,
                                  ): Either[Error, OrganizationEvent] = {
    val maybeValidationError = applyAllValidators[ActivateOrganization](Seq(
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    ))(activateOrganization)

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

  private def activateOrganization(
                                    state: SuspendedState,
                                    activateOrganization: ActivateOrganization,
                            ): Either[Error, OrganizationEvent] = {
    val maybeValidationError = applyAllValidators[ActivateOrganization](Seq(
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    ))(activateOrganization)

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
    val maybeValidationError = applyAllValidators[SuspendOrganization](Seq(
      c => required("organization id", organizationIdValidator)(c.organizationId),
      c => required("on behalf of", memberIdValidator)(c.onBehalfOf)
    ))(suspendOrganization)

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

}
