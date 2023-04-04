package com.improving.app.tenant.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{MemberId, TenantId}
import com.improving.app.tenant.domain.Validation._

import java.time.Instant

object Tenant {
  val TypeKey = EntityTypeKey[TenantCommand]("Tenant")

  /**
   * Wrapper class for the TenantRequest protobuf message with the replyTo ActorRef
   * @param request
   * @param replyTo
   */
  case class TenantCommand(request: TenantRequest, replyTo: ActorRef[StatusReply[TenantEvent]])

  /**
   * State for the Tenant actor
   */
  sealed trait TenantState

  private case object UninitializedTenant extends TenantState

  private sealed trait EstablishedTenantState extends TenantState {
    val info: TenantInfo
    val metaInfo: TenantMetaInfo
  }

  private case class ActiveTenant(info: TenantInfo, metaInfo: TenantMetaInfo) extends EstablishedTenantState

  private case class SuspendedTenant(info: TenantInfo, metaInfo: TenantMetaInfo, suspensionReason: String) extends EstablishedTenantState

  /**
   * Constructor of the Tenant which provides the initial state
   * @param createdBy
   * @return
   */
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

  /**
   * Handler for incoming commands
   */
  private val commandHandler: (TenantState, TenantCommand) => ReplyEffect[TenantEvent, TenantState] = { (state, command) =>
    state match {
      case UninitializedTenant =>
        command.request.asMessage.sealedValue match {
          case TenantRequestMessage.SealedValue.EstablishTenantValue(value) => establishTenant(value, command.replyTo)
          case _ => Effect.reply(command.replyTo)(
            StatusReply.Error("Tenant is not established")
          )
        }
      case establishedState: EstablishedTenantState =>
        command.request.asMessage.sealedValue match {
          case TenantRequestMessage.SealedValue.Empty =>
            Effect.reply(command.replyTo)(
              StatusReply.Error("Command is not supported")
            )
          case TenantRequestMessage.SealedValue.EstablishTenantValue(_) =>
            Effect.reply(command.replyTo)(StatusReply.Error("Tenant is already established"))
          case TenantRequestMessage.SealedValue.ActivateTenantValue(value) => activateTenant(establishedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.SuspendTenantValue(value) => suspendTenant(establishedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.EditInfoValue(value) => editInfo(establishedState, value, command.replyTo)
        }
    }
  }

  /**
   * Handler for incoming events
   */
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

  /**
   * Updates the meta info which includes updating the lastUpdated timestamp
   * @param metaInfo
   * @param lastUpdatedByOpt
   * @return
   */
  private def updateMetaInfo(metaInfo: TenantMetaInfo, lastUpdatedByOpt: Option[MemberId]): TenantMetaInfo = {
    val currentTime = Timestamp(Instant.now())
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(currentTime))
  }

  /**
   * Validation of the preconditions of common fields in the commands
   * @param tenantIdOpt
   * @param updatingUserOpt
   * @return
   */
  private def validateCommonFields(tenantIdOpt: Option[TenantId], updatingUserOpt: Option[MemberId]): Option[String] = {
    val tenantIdInvalidMessageOpt = tenantIdOpt.fold(Option("Tenant Id is not set")) {
      tenantId =>
        if (tenantId.id.isEmpty) {
          Option("Tenant Id is empty")
        } else {
          None
        }
    }

    val updatingUserInvalidMessageOpt = tenantIdInvalidMessageOpt.orElse(updatingUserOpt.fold(Option("Updating user Id is not set")) {
      updatingUser =>
        if (updatingUser.id.isEmpty) {
          Option("Updating user Id is empty")
        } else {
          None
        }
    })

    updatingUserInvalidMessageOpt
  }



  /**
   * Validation for the EstablishTenant command and interaction with the state
   * @param info
   * @param metaInfo
   * @param establishTenant
   * @param replyTo
   * @return
   */
  private def establishTenantLogic(
                                    establishTenant: EstablishTenant,
                                    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val maybeTenantInfoError = required("tenant info", completeTenantInfoValidator)(establishTenant.tenantInfo)
    if(maybeTenantInfoError.isDefined) {
      Effect.reply(replyTo)(StatusReply.Error(maybeTenantInfoError.get.message))
    } else {
      val tenantInfo = establishTenant.tenantInfo.get

      val currentTime = Timestamp(Instant.now())
      val newMetaInfo = TenantMetaInfo(createdBy = establishTenant.establishingUser, createdOn = Some(currentTime))

      val event = TenantEstablished(
        tenantId = establishTenant.tenantId,
        metaInfo = Some(newMetaInfo),
        tenantInfo = Some(tenantInfo)
      )

      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    }
  }

  /**
   * State business logic for establishing the tenant
   * @param state
   * @param establishTenant
   * @param replyTo
   * @return
   */
  private def establishTenant(
    establishTenant: EstablishTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]],
  ): ReplyEffect[TenantEvent, TenantState] = {
    val maybeCommonFieldsError = validateCommonFields(establishTenant.tenantId, establishTenant.establishingUser)
    if(maybeCommonFieldsError.isDefined) {
      Effect.reply(replyTo)(StatusReply.Error(maybeCommonFieldsError.get))
    } else {
      establishTenantLogic(establishTenant, replyTo)
    }
  }

  /**
   * Validation for the ActivateTenant command and interaction with the state
   * @param info
   * @param metaInfo
   * @param activateTenant
   * @param replyTo
   * @return
   */
  private def activateTenantLogic(
                                   info: TenantInfo,
                                   metaInfo: TenantMetaInfo,
                                   activateTenant: ActivateTenant,
                                   replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (info.name.nonEmpty && info.address.isDefined && info.primaryContact.isDefined) {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = activateTenant.activatingUser)
      val event = TenantActivated(
        tenantId = activateTenant.tenantId,
        metaInfo = Some(newMetaInfo)
      )
      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    } else {
      Effect.reply(replyTo)(
        StatusReply.Error("Draft tenants may not transition to the Active state with incomplete required fields")
      )
    }
  }

  /**
   * State business logic for activating the tenant
   * @param state
   * @param activateTenant
   * @param replyTo
   * @return
   */
  private def activateTenant(
                              state: EstablishedTenantState,
                              activateTenant: ActivateTenant,
                              replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val maybeValidationError = applyAllValidators[ActivateTenant](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("activating user", memberIdValidator)(c.activatingUser)
    ))(activateTenant)

    if(maybeValidationError.isDefined) {
      Effect.reply(replyTo)(StatusReply.Error(maybeValidationError.get.message))
    } else {
      state match {
        case ActiveTenant(_, _) =>
          Effect.reply[StatusReply[TenantEvent], TenantEvent, TenantState](replyTo)(
            StatusReply.Error("Active tenants may not transition to the Active state")
          )
        case SuspendedTenant(info, metaInfo, _) =>
          activateTenantLogic(info, metaInfo, activateTenant, replyTo)
      }
    }
  }

  /**
   * Validation for the SuspendTenant command and interaction with the state
   * @param metaInfo
   * @param suspendTenant
   * @param replyTo
   * @return
   */
  private def suspendTenantLogic(
                                  metaInfo: TenantMetaInfo,
                                  suspendTenant: SuspendTenant,
                                  replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = suspendTenant.suspendingUser)
    val event = TenantSuspended(
      tenantId = suspendTenant.tenantId,
      metaInfo = Some(newMetaInfo),
      suspensionReason = suspendTenant.suspensionReason
    )
    Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
  }

  /**
   * State business logic for suspending the tenant
   * @param state
   * @param suspendTenant
   * @param replyTo
   * @return
   */
  private def suspendTenant(
                             state: EstablishedTenantState,
                             suspendTenant: SuspendTenant,
                             replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val maybeValidationError = applyAllValidators[SuspendTenant](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("activating user", memberIdValidator)(c.suspendingUser)
    ))(suspendTenant)

    if (maybeValidationError.isDefined) {
      Effect.reply(replyTo)(StatusReply.Error(maybeValidationError.get.message))
    } else {
      suspendTenantLogic(state.metaInfo, suspendTenant, replyTo)
    }
  }

  private def editInfoLogic(
                               tenantMetaInfo: TenantMetaInfo,
                               editInfo: EditInfo,
                               currentTenantInfo: TenantInfo,
                               replyTo: ActorRef[StatusReply[TenantEvent]]
                             ): ReplyEffect[TenantEvent, TenantState] = {
    val infoToUpdate = editInfo.getInfoToUpdate

    var updatedInfo = currentTenantInfo
    if(infoToUpdate.name.nonEmpty) {
      updatedInfo = updatedInfo.copy(name = infoToUpdate.name)
    }
    if(infoToUpdate.address.isDefined) {
      updatedInfo = updatedInfo.copy(address = infoToUpdate.address)
    }
    if(infoToUpdate.primaryContact.isDefined) {
      updatedInfo = updatedInfo.copy(primaryContact = infoToUpdate.primaryContact)
    }
    if(infoToUpdate.orgs.nonEmpty) {
      updatedInfo = updatedInfo.copy(orgs = infoToUpdate.orgs)
    }

    val newMetaInfo = updateMetaInfo(metaInfo = tenantMetaInfo, lastUpdatedByOpt = editInfo.editingUser)

    val event = InfoEdited(
      tenantId = editInfo.tenantId,
      metaInfo = Some(newMetaInfo),
      oldInfo = Some(currentTenantInfo),
      newInfo = Some(updatedInfo)
    )

    Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
  }
  private def editInfo(
                        state: Tenant.EstablishedTenantState,
                        editInfoCommand: EditInfo,
                        replyTo: ActorRef[StatusReply[TenantEvent]]
                      ): ReplyEffect[TenantEvent, TenantState] = {
    val validationResult = applyAllValidators[EditInfo](Seq(
      c => required("tenant id", tenantIdValidator)(c.tenantId),
      c => required("editing user", memberIdValidator)(c.editingUser),
      c => required("tenant info", partialTenantInfoValidator)(c.infoToUpdate)
    ))(editInfoCommand)

    if(validationResult.isDefined) {
      Effect.reply(replyTo)(
        StatusReply.Error(validationResult.get.message)
      )
    } else {
      editInfoLogic(state.metaInfo, editInfoCommand, state.info, replyTo)
    }
  }
}