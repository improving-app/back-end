package com.improving.app.tenant.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.MemberId
import com.improving.app.tenant.domain.Validation._

import java.time.Instant

object Tenant {
  val TypeKey = EntityTypeKey[TenantCommand]("Tenant")

  case class TenantCommand(request: TenantRequest, replyTo: ActorRef[StatusReply[TenantEvent]])

  sealed trait TenantState

  private case object UninitializedTenant extends TenantState

  private sealed trait EstablishedTenantState extends TenantState {
    val info: TenantInfo
    val metaInfo: TenantMetaInfo
  }

  private case class ActiveTenant(info: TenantInfo, metaInfo: TenantMetaInfo) extends EstablishedTenantState

  private case class SuspendedTenant(info: TenantInfo, metaInfo: TenantMetaInfo, suspensionReason: String) extends EstablishedTenantState

  case class StateError(message: String) extends Error

  def apply(persistenceId: PersistenceId): Behavior[TenantCommand] = {
    Behaviors.setup(context =>
      EventSourcedBehavior[TenantCommand, TenantEvent, TenantState](
        persistenceId = persistenceId,
        emptyState = UninitializedTenant,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    )
  }

  private val commandHandler: (TenantState, TenantCommand) => ReplyEffect[TenantEvent, TenantState] = { (state, command) =>
    val result: Either[Error, TenantEvent] = state match {
      case UninitializedTenant =>
        command.request.asMessage.sealedValue match {
          case TenantRequestMessage.SealedValue.EstablishTenantValue(value) => establishTenant(value)
          case _ => Left(StateError("Tenant is not established"))
        }
      case establishedState: EstablishedTenantState =>
        command.request.asMessage.sealedValue match {
          case TenantRequestMessage.SealedValue.Empty => Left(StateError("Command is not supported"))
          case TenantRequestMessage.SealedValue.EstablishTenantValue(_) => Left(StateError("Tenant is already established"))
          case TenantRequestMessage.SealedValue.ActivateTenantValue(value) => activateTenant(establishedState, value)
          case TenantRequestMessage.SealedValue.SuspendTenantValue(value) => suspendTenant(establishedState, value)
          case TenantRequestMessage.SealedValue.EditInfoValue(value) => editInfo(establishedState, value)
        }
    }
    result match {
      case Left(error) => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
      case Right(event) => Effect.persist(event).thenReply(command.replyTo) { _ => StatusReply.Success(event) }
    }
  }

  private val eventHandler: (TenantState, TenantEvent) => TenantState = { (state, event) =>
    event.asMessage.sealedValue match {
      case TenantEventMessage.SealedValue.Empty => state
      case TenantEventMessage.SealedValue.TenantEstablishedValue(value) =>
        state match {
          case UninitializedTenant => ActiveTenant(info = value.tenantInfo.get, metaInfo = value.metaInfo.get)
          case _: ActiveTenant => state
          case _: SuspendedTenant => state
        }
      case TenantEventMessage.SealedValue.TenantActivatedValue(value) =>
        state match {
          case _: ActiveTenant => state // tenant cannot have TenantActivated in Active state
          case x: SuspendedTenant => ActiveTenant(x.info, value.metaInfo.get)
          case UninitializedTenant => UninitializedTenant
        }
      case TenantEventMessage.SealedValue.TenantSuspendedValue(value) =>
        state match {
          case x: ActiveTenant => SuspendedTenant(x.info, value.metaInfo.get, value.suspensionReason)
          case x: SuspendedTenant => SuspendedTenant(x.info, value.metaInfo.get, value.suspensionReason)
          case UninitializedTenant => UninitializedTenant
        }
      case TenantEventMessage.SealedValue.InfoEditedValue(value) =>
        state match {
          case x: ActiveTenant => x.copy(info = value.getNewInfo, metaInfo = value.getMetaInfo)
          case x: SuspendedTenant => x.copy(info = value.getNewInfo, metaInfo = value.getMetaInfo)
          case UninitializedTenant => UninitializedTenant
        }
    }
  }

  private def updateMetaInfo(metaInfo: TenantMetaInfo, lastUpdatedByOpt: Option[MemberId]): TenantMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def establishTenant(establishTenant: EstablishTenant): Either[Error, TenantEvent] = {
    val maybeValidationError = applyAllValidators[EstablishTenant](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("activating user", memberIdValidator)(c.establishingUser)
    ))(establishTenant)
    if(maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val maybeTenantInfoError = required("tenant info", completeTenantInfoValidator)(establishTenant.tenantInfo)
      if (maybeTenantInfoError.isDefined) {
        Left(maybeTenantInfoError.get)
      } else {
        val tenantInfo = establishTenant.tenantInfo.get

        val newMetaInfo = TenantMetaInfo(
          createdBy = establishTenant.establishingUser,
          createdOn = Some(Timestamp(Instant.now()))
        )

        Right(TenantEstablished(
          tenantId = establishTenant.tenantId,
          metaInfo = Some(newMetaInfo),
          tenantInfo = Some(tenantInfo)
        ))
      }
    }
  }

  private def activateTenant(
                              state: EstablishedTenantState,
                              activateTenant: ActivateTenant,
  ): Either[Error, TenantEvent] = {
    val maybeValidationError = applyAllValidators[ActivateTenant](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("activating user", memberIdValidator)(c.activatingUser)
    ))(activateTenant)

    if(maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      state match {
        case ActiveTenant(_, _) =>
          Left(StateError("Active tenants may not transition to the Active state"))
        case SuspendedTenant(_, metaInfo, _) =>
          val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = activateTenant.activatingUser)
          Right(TenantActivated(
            tenantId = activateTenant.tenantId,
            metaInfo = Some(newMetaInfo)
          ))
      }
    }
  }

  private def suspendTenant(
                             state: EstablishedTenantState,
                             suspendTenant: SuspendTenant,
  ): Either[Error, TenantEvent] = {
    val maybeValidationError = applyAllValidators[SuspendTenant](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("activating user", memberIdValidator)(c.suspendingUser)
    ))(suspendTenant)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = suspendTenant.suspendingUser)
      Right(TenantSuspended(
        tenantId = suspendTenant.tenantId,
        metaInfo = Some(newMetaInfo),
        suspensionReason = suspendTenant.suspensionReason
      ))
    }
  }

  private def editInfo(
                        state: Tenant.EstablishedTenantState,
                        editInfoCommand: EditInfo,
                      ): Either[Error, TenantEvent] = {
    val validationResult = applyAllValidators[EditInfo](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("editing user", memberIdValidator)(c.editingUser),
      c => required("tenant info", partialTenantInfoValidator)(c.infoToUpdate)
    ))(editInfoCommand)

    if(validationResult.isDefined) {
      Left(validationResult.get)
    } else {
        val infoToUpdate = editInfoCommand.getInfoToUpdate
        var updatedInfo = state.info
        if (infoToUpdate.name.nonEmpty) {
          updatedInfo = updatedInfo.copy(name = infoToUpdate.name)
        }
        if (infoToUpdate.address.isDefined) {
          updatedInfo = updatedInfo.copy(address = infoToUpdate.address)
        }
        if (infoToUpdate.primaryContact.isDefined) {
          updatedInfo = updatedInfo.copy(primaryContact = infoToUpdate.primaryContact)
        }
        if (infoToUpdate.organizations.isDefined) {
          updatedInfo = updatedInfo.copy(organizations = infoToUpdate.organizations)
        }

        val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = editInfoCommand.editingUser)

        Right(InfoEdited(
          tenantId = editInfoCommand.tenantId,
          metaInfo = Some(newMetaInfo),
          oldInfo = Some(state.info),
          newInfo = Some(updatedInfo)
        ))
    }
  }
}