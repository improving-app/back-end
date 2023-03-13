package com.improving.app.tenant.domain

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import com.improving.app.common.MemberId

object Tenant {

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

  case class DraftTenant(info: Info, metaInfo: MetaInfo) extends TenantState

  case class ActiveTenant(info: Info, metaInfo: MetaInfo) extends TenantState

  case class SuspendedTenant(info: Info, metaInfo: MetaInfo, suspensionReason: String) extends TenantState

  /**
   * Constructor of the Tenant which provides the initial state
   * @param createdBy
   * @return
   */
  def apply(createdBy: MemberId): Behavior[TenantCommand] = {
    Behaviors.setup(context =>
      EventSourcedBehavior[TenantCommand, TenantEvent, TenantState](
        persistenceId = PersistenceId.ofUniqueId(createdBy.id),
        emptyState = DraftTenant(
          info = Info(),
          metaInfo = MetaInfo(
            createdBy = Some(createdBy),
            createdOn = Some(Timestamp(Instant.now()))
          )
        ),
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    )
  }

  /**
   * Handler for incoming commands
   */
  private val commandHandler: (TenantState, TenantCommand) => ReplyEffect[TenantEvent, TenantState] = { (state, command) =>
    command.request.asMessage.sealedValue match {
      case TenantRequestMessage.SealedValue.Empty =>
        Effect.reply(command.replyTo)(
          StatusReply.Error("Command is not supported")
        )
      case TenantRequestMessage.SealedValue.UpdateTenantNameValue(value) => updateTenantName(state, value, command.replyTo)
      case TenantRequestMessage.SealedValue.UpdatePrimaryContactValue(value) => updatePrimaryContact(state, value, command.replyTo)
      case TenantRequestMessage.SealedValue.UpdateAddressValue(value) => updateAddress(state, value, command.replyTo)
      case TenantRequestMessage.SealedValue.ActivateTenantValue(value) => activateTenant(state, value, command.replyTo)
      case TenantRequestMessage.SealedValue.SuspendTenantValue(value) => suspendTenant(state, value, command.replyTo)
    }
  }

  /**
   * Handler for incoming events
   */
  private val eventHandler: (TenantState, TenantEvent) => TenantState = { (state, event) =>
    event.asMessage.sealedValue match {
      case TenantEventMessage.SealedValue.Empty => state
      case TenantEventMessage.SealedValue.TenantNameUpdatedValue(value) =>
        state match {
          case x: DraftTenant => x.copy(info = x.info.copy(name = value.newName), metaInfo = value.metaInfo.get)
          case x: ActiveTenant => x.copy(info = x.info.copy(name = value.newName), metaInfo = value.metaInfo.get)
          case x: SuspendedTenant => x.copy(info = x.info.copy(name = value.newName), metaInfo = value.metaInfo.get)
        }
      case TenantEventMessage.SealedValue.PrimaryContactUpdatedValue(value) =>
        state match {
          case x: DraftTenant => x.copy(info = x.info.copy(contact = value.newContact), metaInfo = value.metaInfo.get)
          case x: ActiveTenant => x.copy(info = x.info.copy(contact = value.newContact), metaInfo = value.metaInfo.get)
          case x: SuspendedTenant => x.copy(info = x.info.copy(contact = value.newContact), metaInfo = value.metaInfo.get)
        }
      case TenantEventMessage.SealedValue.AddressUpdatedValue(value) =>
        state match {
          case x: DraftTenant => x.copy(info = x.info.copy(address = value.newAddress), metaInfo = value.metaInfo.get)
          case x: ActiveTenant => x.copy(info = x.info.copy(address = value.newAddress), metaInfo = value.metaInfo.get)
          case x: SuspendedTenant => x.copy(info = x.info.copy(address = value.newAddress), metaInfo = value.metaInfo.get)
        }
      case TenantEventMessage.SealedValue.TenantActivatedValue(value) =>
        state match {
          case x: DraftTenant => ActiveTenant(x.info, value.metaInfo.get)
          case _: ActiveTenant => state // tenant cannot have TenantActivated in Active state
          case x: SuspendedTenant => ActiveTenant(x.info, value.metaInfo.get)
        }
      case TenantEventMessage.SealedValue.TenantSuspendedValue(value) =>
        state match {
          case x: DraftTenant => SuspendedTenant(x.info, value.metaInfo.get, value.suspensionReason)
          case x: ActiveTenant => SuspendedTenant(x.info, value.metaInfo.get, value.suspensionReason)
          case x: SuspendedTenant => SuspendedTenant(x.info, value.metaInfo.get, value.suspensionReason)
        }
    }
  }

  /**
   * Updates the meta info which includes updating the lastUpdated timestamp
   * @param metaInfo
   * @param lastUpdatedByOpt
   * @return
   */
  private def updateMetaInfo(metaInfo: MetaInfo, lastUpdatedByOpt: Option[MemberId]): MetaInfo = {
    val currentTime = Timestamp(Instant.now())
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(currentTime))
  }

  /**
   * Validation and business logic for updating the tenant name
   * @param info
   * @param metaInfo
   * @param updateTenantNameCommand
   * @param replyTo
   * @return
   */
  private def updateTenantNameLogic(
    info: Info, metaInfo: MetaInfo,
    updateTenantNameCommand: UpdateTenantName,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (info.name.equals(updateTenantNameCommand.newName)) {
      Effect.reply(replyTo)(
        StatusReply.Error("Tenant name is already in use")
      )
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = updateTenantNameCommand.updatingUser)
      val event = TenantNameUpdated(
        tenantId = updateTenantNameCommand.tenantId,
        oldName = info.name,
        newName = updateTenantNameCommand.newName,
        metaInfo = Some(newMetaInfo)
      )

      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    }
  }

  /**
   * State business logic for updating the tenant name
   * @param state
   * @param updateTenantNameCommand
   * @param replyTo
   * @return
   */
  private def updateTenantName(
    state: TenantState,
    updateTenantNameCommand: UpdateTenantName,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    state match {
      case DraftTenant(info, metaInfo) =>
        updateTenantNameLogic(info, metaInfo, updateTenantNameCommand, replyTo)
      case ActiveTenant(info, metaInfo) =>
        updateTenantNameLogic(info, metaInfo, updateTenantNameCommand, replyTo)
      case SuspendedTenant(info, metaInfo, _) =>
        updateTenantNameLogic(info, metaInfo, updateTenantNameCommand, replyTo)
    }
  }

  /**
   * Validation and business logic for updating the primary contact
   * @param info
   * @param metaInfo
   * @param updatePrimaryContact
   * @param replyTo
   * @return
   */
  private def updatePrimaryContactLogic(
    info:Info,
    metaInfo: MetaInfo,
    updatePrimaryContact: UpdatePrimaryContact,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (updatePrimaryContact.newContact.isDefined &&
      updatePrimaryContact.newContact.get.phone != "" &&
      updatePrimaryContact.newContact.get.emailAddress != "" &&
      updatePrimaryContact.newContact.get.firstName != "" &&
      updatePrimaryContact.newContact.get.lastName != "" &&
      updatePrimaryContact.newContact.get.userName != ""
    ) {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = updatePrimaryContact.updatingUser)
      val event = PrimaryContactUpdated(
        tenantId = updatePrimaryContact.tenantId,
        oldContact = info.contact,
        newContact = updatePrimaryContact.newContact,
        metaInfo = Some(newMetaInfo)
      )

      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    } else {
      Effect.reply(replyTo)(
        StatusReply.Error("Primary contact info is not complete")
      )
    }
  }

  /**
   * State business logic for updating the primary contact
   * @param state
   * @param updatePrimaryContact
   * @param replyTo
   * @return
   */
  private def updatePrimaryContact(
    state: TenantState,
    updatePrimaryContact: UpdatePrimaryContact,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    state match {
      case DraftTenant(info, metaInfo) =>
        updatePrimaryContactLogic(info, metaInfo, updatePrimaryContact, replyTo)
      case ActiveTenant(info, metaInfo) =>
        updatePrimaryContactLogic(info, metaInfo, updatePrimaryContact, replyTo)
      case SuspendedTenant(info, metaInfo, _) =>
        updatePrimaryContactLogic(info, metaInfo, updatePrimaryContact, replyTo)
    }
  }

  /**
   * Validation and business logic for updating the address
   * @param info
   * @param metaInfo
   * @param updateAddress
   * @param replyTo
   * @return
   */
  private def updateAddressLogic(
    info: Info, metaInfo: MetaInfo,
    updateAddress: UpdateAddress,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (
      updateAddress.updatingUser.isDefined &&
        updateAddress.tenantId.isDefined &&
        updateAddress.newAddress.fold(false)(
          address =>
            address.city.nonEmpty &&
            address.country.nonEmpty &&
            address.line1.nonEmpty &&
            address.postalCode.isDefined &&
            address.stateProvince.nonEmpty
            // There is an assumption here that line2 does not need to be validated
        )
    ) {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = updateAddress.updatingUser)
      val event = AddressUpdated(
        tenantId = updateAddress.tenantId,
        oldAddress = info.address,
        newAddress = updateAddress.newAddress,
        metaInfo = Some(newMetaInfo)
      )
      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    } else {
      Effect.reply(replyTo)(
        StatusReply.Error("Address information is not complete")
      )
    }
  }

  /**
   * State business logic for updating the address
   * @param state
   * @param updateAddress
   * @param replyTo
   * @return
   */
  private def updateAddress(
    state: TenantState,
    updateAddress: UpdateAddress,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    state match {
      case DraftTenant(info, metaInfo) =>
        updateAddressLogic(info, metaInfo, updateAddress, replyTo)
      case ActiveTenant(info, metaInfo) =>
        updateAddressLogic(info, metaInfo, updateAddress, replyTo)
      case SuspendedTenant(info, metaInfo, _) =>
        updateAddressLogic(info, metaInfo, updateAddress, replyTo)
    }
  }

  /**
   * Validation and business logic for activating the tenant
   * @param info
   * @param metaInfo
   * @param activateTenant
   * @param replyTo
   * @return
   */
  private def activateTenantLogic(
    info: Info,
    metaInfo: MetaInfo,
    activateTenant: ActivateTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (info.name.nonEmpty && info.address.isDefined && info.contact.isDefined) {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = activateTenant.updatingUser)
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
    state: TenantState,
    activateTenant: ActivateTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    state match {
      case DraftTenant(info, metaInfo) =>
        activateTenantLogic(info, metaInfo, activateTenant, replyTo)
      case ActiveTenant(_, _) =>
        Effect.reply(replyTo)(
          StatusReply.Error("Active tenants may not transition to the Active state")
        )
      case SuspendedTenant(info, metaInfo, _) =>
        activateTenantLogic(info, metaInfo, activateTenant, replyTo)
    }
  }

  /**
   * Validation and business logic for activating the tenant
   * @param metaInfo
   * @param suspendTenant
   * @param replyTo
   * @return
   */
  private def suspendTenantLogic(
    metaInfo: MetaInfo,
    suspendTenant: SuspendTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = suspendTenant.updatingUser)
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
    state: TenantState,
    suspendTenant: SuspendTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    state match {
      case DraftTenant(_, metaInfo) =>
        suspendTenantLogic(metaInfo, suspendTenant, replyTo)
      case ActiveTenant(_, metaInfo) =>
        suspendTenantLogic(metaInfo, suspendTenant, replyTo)
      case SuspendedTenant(_, metaInfo, _) =>
        suspendTenantLogic(metaInfo, suspendTenant, replyTo)
    }
  }
}