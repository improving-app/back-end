package com.improving.app.tenant.domain

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import com.improving.app.common.domain.{CaPostalCodeImpl, MemberId, OrganizationId, TenantId, UsPostalCodeImpl}

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

  private sealed trait InitializedTenantState extends TenantState {
    val info: TenantInfo
    val metaInfo: TenantMetaInfo
  }

  private case class ActiveTenant(info: TenantInfo, metaInfo: TenantMetaInfo) extends InitializedTenantState

  private case class SuspendedTenant(info: TenantInfo, metaInfo: TenantMetaInfo, suspensionReason: String) extends InitializedTenantState

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
      case initializedState: InitializedTenantState =>
        command.request.asMessage.sealedValue match {
          case TenantRequestMessage.SealedValue.Empty =>
            Effect.reply(command.replyTo)(
              StatusReply.Error("Command is not supported")
            )
          case TenantRequestMessage.SealedValue.EstablishTenantValue(_) =>
            Effect.reply(command.replyTo)(StatusReply.Error("Tenant is already established"))
          case TenantRequestMessage.SealedValue.UpdateTenantNameValue(value) => updateTenantName(initializedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.UpdatePrimaryContactValue(value) => updatePrimaryContact(initializedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.UpdateAddressValue(value) => updateAddress(initializedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.AddOrganizationsValue(value) => addOrganizations(initializedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.RemoveOrganizationsValue(value) => removeOrganizations(initializedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.ActivateTenantValue(value) => activateTenant(initializedState, value, command.replyTo)
          case TenantRequestMessage.SealedValue.SuspendTenantValue(value) => suspendTenant(initializedState, value, command.replyTo)
        }
    }
  }

  /**
   * The new state of a tenant when OrganizationsAdded or OrganizationsRemoved event has happened.
   * @param state
   * @param orgList
   * @param metaInfo
   * @return
   */
  private def updateInfoForOrganizationEvent(
    state: TenantState,
    orgList: Seq[OrganizationId],
    metaInfo: TenantMetaInfo
  ): TenantState = {
    state match {
      case x: ActiveTenant => x.copy(info = x.info.copy(orgs = orgList), metaInfo = metaInfo)
      case x: SuspendedTenant => x.copy(info = x.info.copy(orgs = orgList), metaInfo = metaInfo)
      case UninitializedTenant => UninitializedTenant
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
      case TenantEventMessage.SealedValue.TenantNameUpdatedValue(value) =>
        state match {
          case x: ActiveTenant => x.copy(info = x.info.copy(name = value.newName), metaInfo = value.metaInfo.get)
          case x: SuspendedTenant => x.copy(info = x.info.copy(name = value.newName), metaInfo = value.metaInfo.get)
          case UninitializedTenant => UninitializedTenant
        }
      case TenantEventMessage.SealedValue.PrimaryContactUpdatedValue(value) =>
        state match {
          case x: ActiveTenant => x.copy(info = x.info.copy(primaryContact = value.newContact), metaInfo = value.metaInfo.get)
          case x: SuspendedTenant => x.copy(info = x.info.copy(primaryContact = value.newContact), metaInfo = value.metaInfo.get)
          case UninitializedTenant => UninitializedTenant
        }
      case TenantEventMessage.SealedValue.AddressUpdatedValue(value) =>
        state match {
          case x: ActiveTenant => x.copy(info = x.info.copy(address = value.newAddress), metaInfo = value.metaInfo.get)
          case x: SuspendedTenant => x.copy(info = x.info.copy(address = value.newAddress), metaInfo = value.metaInfo.get)
          case UninitializedTenant => UninitializedTenant
        }
      case TenantEventMessage.SealedValue.OrganizationsAddedValue(value) =>
            updateInfoForOrganizationEvent(state, value.newOrgsList, value.metaInfo.get)
      case TenantEventMessage.SealedValue.OrganizationsRemovedValue(value) =>
            updateInfoForOrganizationEvent(state, value.newOrgsList, value.metaInfo.get)
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

  private def validateTenantInfo(maybeInfo: Option[TenantInfo]): Option[String] = {
    if(maybeInfo.isEmpty) {
      Some("TenantInfo is missing")
    } else {
      val info = maybeInfo.get
      if(info.name.isEmpty) {
        Some("Tenant name is empty")
        // TODO: other checks
      } else {
        None
      }
    }
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
    val maybeTenantInfoError = validateTenantInfo(establishTenant.tenantInfo)
    if(maybeTenantInfoError.isDefined) {
      Effect.reply(replyTo)(StatusReply.Error(maybeTenantInfoError.get))
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
   * Validation of the preconditions of the UpdateTenantName command
   * @param updateTenantNameCommand
   * @return
   */
  private def validateUpdateTenantNameCommand(updateTenantNameCommand: UpdateTenantName): Option[String] = {
    val commonFieldsInvalidMessageOpt = validateCommonFields(
      tenantIdOpt = updateTenantNameCommand.tenantId,
      updatingUserOpt = updateTenantNameCommand.updatingUser
    )

    commonFieldsInvalidMessageOpt.orElse(
      if (updateTenantNameCommand.newName.isEmpty) {
        Option("Updating tenant name is empty")
      } else {
        None
      }
    )
  }

  /**
   * Validation for the UpdateTenantName command and interaction with the state
   * @param info
   * @param metaInfo
   * @param updateTenantNameCommand
   * @param replyTo
   * @return
   */
  private def updateTenantNameLogic(
                                     info: TenantInfo, metaInfo: TenantMetaInfo,
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
    state: InitializedTenantState,
    updateTenantNameCommand: UpdateTenantName,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateUpdateTenantNameCommand(updateTenantNameCommand)
    preconditionMessageOpt.fold(
      updateTenantNameLogic(state.info, state.metaInfo, updateTenantNameCommand, replyTo)
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
    }
  }

  /**
   * Validation of the preconditions of the UpdatePrimaryContact command
   *
   * @param updateTenantNameCommand
   * @return
   */
  private def validateUpdatePrimaryContactPreconditions(updatePrimaryContact: UpdatePrimaryContact): Option[String] = {
    val commonFieldsInvalidMessageOpt = validateCommonFields(
      tenantIdOpt = updatePrimaryContact.tenantId,
      updatingUserOpt = updatePrimaryContact.updatingUser
    )

    commonFieldsInvalidMessageOpt.orElse(
      updatePrimaryContact.newContact.fold(Option("Primary contact info is not complete")) {
        newContact =>
          if (
            newContact.firstName.isEmpty ||
              newContact.lastName.isEmpty ||
              newContact.emailAddress.forall(_.isEmpty) ||
              newContact.phone.forall(_.isEmpty) ||
              newContact.userName.isEmpty
          ) {
            Some("Primary contact info is not complete")
          } else {
            None
          }
      }
    )
  }

  /**
   * Validation for the UpdatePrimaryContact command and interaction with the state
   * @param info
   * @param metaInfo
   * @param updatePrimaryContact
   * @param replyTo
   * @return
   */
  private def updatePrimaryContactLogic(
                                         info:TenantInfo,
                                         metaInfo: TenantMetaInfo,
                                         updatePrimaryContact: UpdatePrimaryContact,
                                         replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = updatePrimaryContact.updatingUser)
    val event = PrimaryContactUpdated(
      tenantId = updatePrimaryContact.tenantId,
      oldContact = info.primaryContact,
      newContact = updatePrimaryContact.newContact,
      metaInfo = Some(newMetaInfo)
    )

    Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
  }

  /**
   * State business logic for updating the primary contact
   * @param state
   * @param updatePrimaryContact
   * @param replyTo
   * @return
   */
  private def updatePrimaryContact(
    state: InitializedTenantState,
    updatePrimaryContact: UpdatePrimaryContact,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateUpdatePrimaryContactPreconditions(updatePrimaryContact)
    preconditionMessageOpt.fold(
      updatePrimaryContactLogic(state.info, state.metaInfo, updatePrimaryContact, replyTo)
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
    }
  }

  /**
   * Validation of the preconditions of the UpdateAddress command
   * @param updateAddress
   * @return
   */
  private def validateUpdateAddressPreconditions(updateAddress: UpdateAddress): Option[String] = {
    val commonFieldsInvalidMessageOpt = validateCommonFields(
      tenantIdOpt = updateAddress.tenantId,
      updatingUserOpt = updateAddress.updatingUser
    )

    commonFieldsInvalidMessageOpt.orElse(
      updateAddress.newAddress.fold(Option("Address information is not complete")) {
        newAddress =>
          val isPostalCodeMissing = newAddress.postalCode.fold(true) {
            postalCode =>
              postalCode.postalCodeValue match {
                case UsPostalCodeImpl(code) => code.isEmpty
                case CaPostalCodeImpl(code) => code.isEmpty
              }
          }
          if (
            newAddress.line1.isEmpty ||
              newAddress.city.isEmpty ||
              newAddress.stateProvince.isEmpty ||
              newAddress.country.isEmpty ||
              isPostalCodeMissing
          ) {
            Option("Address information is not complete")
          } else {
            None
          }
      }
    )
  }

  /**
   * Validation for the UpdateAddress command and interaction with the state
   * @param info
   * @param metaInfo
   * @param updateAddress
   * @param replyTo
   * @return
   */
  private def updateAddressLogic(
                                  info: TenantInfo, metaInfo: TenantMetaInfo,
                                  updateAddress: UpdateAddress,
                                  replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = updateAddress.updatingUser)
    val event = AddressUpdated(
      tenantId = updateAddress.tenantId,
      oldAddress = info.address,
      newAddress = updateAddress.newAddress,
      metaInfo = Some(newMetaInfo)
    )
    Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
  }

  /**
   * State business logic for updating the address
   * @param state
   * @param updateAddress
   * @param replyTo
   * @return
   */
  private def updateAddress(
    state: InitializedTenantState,
    updateAddress: UpdateAddress,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateUpdateAddressPreconditions(updateAddress)
    preconditionMessageOpt.fold(
      updateAddressLogic(state.info, state.metaInfo, updateAddress, replyTo)
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
    }
  }

  /**
   * Validation of the preconditions of the AddOrganizations command
   * @param addOrganizations
   * @return
   */
  private def validateAddOrganizationsPreconditions(addOrganizations: AddOrganizations): Option[String] = {
    val commonFieldsInvalidMessageOpt = validateCommonFields(
      tenantIdOpt = addOrganizations.tenantId,
      updatingUserOpt = addOrganizations.updatingUser
    )

    commonFieldsInvalidMessageOpt.orElse(
      if (addOrganizations.orgId.isEmpty) {
        Option("No organizations to add")
      } else if (addOrganizations.orgId.exists(_.id.isEmpty)) {
        Option("Empty organization ids are not allowed")
      } else {
        None
      }
    )
  }

  /**
   * Validation for the AddOrganizations command and interaction with the state
   * @param info
   * @param metaInfo
   * @param addOrganizations
   * @param replyTo
   * @return
   */
  private def addOrganizationsLogic(
                                     info: TenantInfo,
                                     metaInfo: TenantMetaInfo,
                                     addOrganizations: AddOrganizations,
                                     replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (addOrganizations.orgId.exists(info.orgs.contains(_))) {
      Effect.reply(replyTo)(
        StatusReply.Error("Organization ids already present for the tenant is not allowed")
      )
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = addOrganizations.updatingUser)
      val newOrgIds = info.orgs ++ addOrganizations.orgId.distinct
      val event = OrganizationsAdded(
        tenantId = addOrganizations.tenantId,
        newOrgsList = newOrgIds,
        metaInfo = Some(newMetaInfo)
      )

      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    }
  }

  /**
   * State business logic for adding organizations
   * @param state
   * @param addOrganizations
   * @param replyTo
   * @return
   */
  private def addOrganizations(
    state: InitializedTenantState,
    addOrganizations: AddOrganizations,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateAddOrganizationsPreconditions(addOrganizations)
    preconditionMessageOpt.fold(
      addOrganizationsLogic(state.info, state.metaInfo, addOrganizations, replyTo)
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
    }
  }

  /**
   * Validation of the preconditions of the RemoveOrganizations command
   *
   * @param removeOrganizations
   * @return
   */
  private def validateRemoveOrganizationsPreconditions(removeOrganizations: RemoveOrganizations): Option[String] = {
    val commonFieldsInvalidMessageOpt = validateCommonFields(
      tenantIdOpt = removeOrganizations.tenantId,
      updatingUserOpt = removeOrganizations.updatingUser
    )

    commonFieldsInvalidMessageOpt.orElse(
      if (removeOrganizations.orgId.isEmpty) {
        Option("No organizations to remove")
      } else if (removeOrganizations.orgId.exists(_.id.isEmpty)) {
        Option("Empty organization ids are not allowed")
      } else {
        None
      }
    )
  }

  /**
   * Validation for the RemoveOrganizations command and interaction with the state
   * @param info
   * @param metaInfo
   * @param removeOrganizations
   * @param replyTo
   * @return
   */
  private def removeOrganizationsLogic(
                                        info: TenantInfo,
                                        metaInfo: TenantMetaInfo,
                                        removeOrganizations: RemoveOrganizations,
                                        replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    if (removeOrganizations.orgId.exists(!info.orgs.contains(_))) {
      Effect.reply(replyTo)(
        StatusReply.Error("Organization ids not already present for the tenant is not allowed")
      )
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = metaInfo, lastUpdatedByOpt = removeOrganizations.updatingUser)
      val newOrgIds = info.orgs.filterNot(removeOrganizations.orgId.contains)
      val event = OrganizationsRemoved(
        tenantId = removeOrganizations.tenantId,
        newOrgsList = newOrgIds,
        metaInfo = Some(newMetaInfo)
      )

      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(event) }
    }
  }

  /**
   * State business logic for removing organizations
   * @param state
   * @param removeOrganizations
   * @param replyTo
   * @return
   */
  private def removeOrganizations(
    state: InitializedTenantState,
    removeOrganizations: RemoveOrganizations,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateRemoveOrganizationsPreconditions(removeOrganizations)
    preconditionMessageOpt.fold(
      removeOrganizationsLogic(state.info, state.metaInfo, removeOrganizations, replyTo)
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
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
    state: InitializedTenantState,
    activateTenant: ActivateTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateCommonFields(activateTenant.tenantId, activateTenant.activatingUser)
    preconditionMessageOpt.fold(
      state match {
        case ActiveTenant(_, _) =>
          Effect.reply[StatusReply[TenantEvent], TenantEvent, TenantState](replyTo)(
            StatusReply.Error("Active tenants may not transition to the Active state")
          )
        case SuspendedTenant(info, metaInfo, _) =>
          activateTenantLogic(info, metaInfo, activateTenant, replyTo)
      }
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
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
    state: InitializedTenantState,
    suspendTenant: SuspendTenant,
    replyTo: ActorRef[StatusReply[TenantEvent]]
  ): ReplyEffect[TenantEvent, TenantState] = {
    val preconditionMessageOpt = validateCommonFields(suspendTenant.tenantId, suspendTenant.suspendingUser)
    preconditionMessageOpt.fold(
      state match {
        case ActiveTenant(_, metaInfo) =>
          suspendTenantLogic(metaInfo, suspendTenant, replyTo)
        case SuspendedTenant(_, metaInfo, _) =>
          suspendTenantLogic(metaInfo, suspendTenant, replyTo)
      }
    ) {
      message =>
        Effect.reply(replyTo)(
          StatusReply.Error(message)
        )
    }
  }
}