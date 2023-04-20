package com.improving.app.tenant.domain

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
import com.improving.app.tenant.domain.Validation._

import java.time.Instant

object Tenant {
  val TypeKey: EntityTypeKey[TenantCommand] = EntityTypeKey[TenantCommand]("Tenant")

  case class TenantCommand(request: TenantRequest, replyTo: ActorRef[StatusReply[TenantEnvelope]])

  sealed trait TenantState

  private case object UninitializedTenant extends TenantState

  sealed private trait EstablishedTenantState extends TenantState {
    val info: TenantInfo
    val metaInfo: TenantMetaInfo
  }

  private case class TerminatedTenant(metaInfo: TenantMetaInfo) extends TenantState

  private case class ActiveTenant(info: TenantInfo, metaInfo: TenantMetaInfo) extends EstablishedTenantState

  private case class SuspendedTenant(info: TenantInfo, metaInfo: TenantMetaInfo, suspensionReason: String)
      extends EstablishedTenantState

  def apply(persistenceId: PersistenceId): Behavior[TenantCommand] = {
    Behaviors.setup(context =>
      EventSourcedBehavior[TenantCommand, TenantEnvelope, TenantState](
        persistenceId = persistenceId,
        emptyState = UninitializedTenant,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    )
  }

  private val commandHandler: (TenantState, TenantCommand) => ReplyEffect[TenantEnvelope, TenantState] = {
    (state, command) =>
      val result: Either[Error, TenantEnvelope] = state match {
        case UninitializedTenant =>
          command.request match {
            case x: EstablishTenant  => establishTenant(x)
            case x: GetOrganizations => getOrganizations(x)
            case _                   => Left(StateError("Tenant is not established"))
          }
        case establishedState: EstablishedTenantState =>
          establishedState match {
            case activeTenantState: ActiveTenant =>
              command.request match {
                case _: EstablishTenant  => Left(StateError("Tenant is already established"))
                case _: ActivateTenant   => Left(StateError("Active tenants may not transition to the Active state"))
                case x: SuspendTenant    => suspendTenant(establishedState, x)
                case x: EditInfo         => editInfo(establishedState, x)
                case x: GetOrganizations => getOrganizations(x, Some(activeTenantState))
                case x: TerminateTenant  => terminateTenant(establishedState, x)
                case _                   => Left(StateError("Command is not supported"))
              }
            case suspendedTenantState: SuspendedTenant =>
              command.request match {
                case _: EstablishTenant  => Left(StateError("Tenant is already established"))
                case x: ActivateTenant   => activateTenant(establishedState, x)
                case x: SuspendTenant    => suspendTenant(establishedState, x)
                case x: EditInfo         => editInfo(establishedState, x)
                case x: GetOrganizations => getOrganizations(x, Some(suspendedTenantState))
                case x: TerminateTenant  => terminateTenant(establishedState, x)
                case _                   => Left(StateError("Command is not supported"))
              }
          }
        case _: TerminatedTenant =>
          command.request match {
            case x: GetOrganizations => getOrganizations(x)
            case _                   => Left(StateError("Command not allowed in Terminated state"))
          }
      }
      result match {
        case Left(error) => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
        case Right(response) =>
          response match {
            case _: TenantDataResponse => Effect.reply(command.replyTo) { StatusReply.Success(response) }
            case _: TenantEventResponse =>
              Effect.persist(response).thenReply(command.replyTo) { _ => StatusReply.Success(response) }
            case _ =>
              Effect.reply(command.replyTo)(
                StatusReply.Error(s"${response.productPrefix} is not a supported member response")
              )
          }
      }
  }

  private val eventHandler: (TenantState, TenantEnvelope) => TenantState = { (state, response) =>
    response match {
      case event: TenantEventResponse =>
        event.tenantEvent match {
          case e: TenantEstablished =>
            state match {
              case UninitializedTenant => ActiveTenant(info = e.tenantInfo.get, metaInfo = e.metaInfo.get)
              case _: ActiveTenant     => state
              case _: SuspendedTenant  => state
              case _: TerminatedTenant => state
            }
          case e: TenantActivated =>
            state match {
              case _: ActiveTenant     => state // tenant cannot have TenantActivated in Active state
              case x: SuspendedTenant  => ActiveTenant(x.info, e.metaInfo.get)
              case UninitializedTenant => UninitializedTenant
              case _: TerminatedTenant => state
            }
          case e: TenantSuspended =>
            state match {
              case x: ActiveTenant     => SuspendedTenant(x.info, e.metaInfo.get, e.suspensionReason)
              case x: SuspendedTenant  => SuspendedTenant(x.info, e.metaInfo.get, e.suspensionReason)
              case UninitializedTenant => UninitializedTenant
              case _: TerminatedTenant => state
            }
          case e: InfoEdited =>
            state match {
              case x: ActiveTenant     => x.copy(info = e.getNewInfo, metaInfo = e.getMetaInfo)
              case x: SuspendedTenant  => x.copy(info = e.getNewInfo, metaInfo = e.getMetaInfo)
              case UninitializedTenant => UninitializedTenant
              case _: TerminatedTenant => state
            }
          case _: TenantTerminated =>
            state match {
              case x: EstablishedTenantState => TerminatedTenant(x.metaInfo)
              case _                         => state
            }
          case _ => state
        }
      case _ => state
    }
  }

  private def updateMetaInfo(metaInfo: TenantMetaInfo, lastUpdatedByOpt: Option[MemberId]): TenantMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedByOpt, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def establishTenant(establishTenant: EstablishTenant): Either[Error, TenantEnvelope] = {
    val maybeValidationError = applyAllValidators[EstablishTenant](
      Seq(
        c => required("tenant id", tenantIdValidator)(c.tenantId),
        c => required("activating user", memberIdValidator)(c.establishingUser)
      )
    )(establishTenant)
    if (maybeValidationError.isDefined) {
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

        Right(
          TenantEventResponse(
            TenantEstablished(
              tenantId = establishTenant.tenantId,
              metaInfo = Some(newMetaInfo),
              tenantInfo = Some(tenantInfo)
            )
          )
        )
      }
    }
  }

  private def activateTenant(
      state: EstablishedTenantState,
      activateTenant: ActivateTenant,
  ): Either[Error, TenantEnvelope] = {
    val maybeValidationError = applyAllValidators[ActivateTenant](
      Seq(
        c => required("tenant id", tenantIdValidator)(c.tenantId),
        c => required("activating user", memberIdValidator)(c.activatingUser)
      )
    )(activateTenant)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = activateTenant.activatingUser)
      Right(
        TenantEventResponse(
          TenantActivated(
            tenantId = activateTenant.tenantId,
            metaInfo = Some(newMetaInfo)
          )
        )
      )
    }
  }

  private def suspendTenant(
      state: EstablishedTenantState,
      suspendTenant: SuspendTenant,
  ): Either[Error, TenantEnvelope] = {
    val maybeValidationError = applyAllValidators[SuspendTenant](
      Seq(
        c => required("tenant id", tenantIdValidator)(c.tenantId),
        c => required("activating user", memberIdValidator)(c.suspendingUser)
      )
    )(suspendTenant)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = suspendTenant.suspendingUser)
      Right(
        TenantEventResponse(
          TenantSuspended(
            tenantId = suspendTenant.tenantId,
            metaInfo = Some(newMetaInfo),
            suspensionReason = suspendTenant.suspensionReason
          )
        )
      )
    }
  }

  private def editInfo(
      state: Tenant.EstablishedTenantState,
      editInfoCommand: EditInfo,
  ): Either[Error, TenantEnvelope] = {
    val validationResult = applyAllValidators[EditInfo](
      Seq(
        c => required("tenant id", tenantIdValidator)(c.tenantId),
        c => required("editing user", memberIdValidator)(c.editingUser),
        c => required("tenant info", partialTenantInfoValidator)(c.infoToUpdate)
      )
    )(editInfoCommand)

    if (validationResult.isDefined) {
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

      Right(
        TenantEventResponse(
          InfoEdited(
            tenantId = editInfoCommand.tenantId,
            metaInfo = Some(newMetaInfo),
            oldInfo = Some(state.info),
            newInfo = Some(updatedInfo)
          )
        )
      )
    }
  }

  private def getOrganizations(
      getOrganizationsQuery: GetOrganizations,
      stateOpt: Option[Tenant.EstablishedTenantState] = None
  ): Either[Error, TenantEnvelope] = {
    val validationResult = applyAllValidators[GetOrganizations](
      Seq(c => required("tenant id", tenantIdValidator)(c.tenantId))
    )(getOrganizationsQuery)

    if (validationResult.isDefined) {
      Left(validationResult.get)
    } else {
      Right(
        TenantDataResponse(
          TenantOrganizationData(
            organizations = stateOpt.fold[Option[TenantOrganizationList]](Some(TenantOrganizationList(Seq.empty))) {
              _.info.organizations
            }
          )
        )
      )
    }
  }

  private def terminateTenant(
      state: EstablishedTenantState,
      terminateTenant: TerminateTenant,
  ): Either[Error, TenantEnvelope] = {
    val maybeValidationError = applyAllValidators[TerminateTenant](
      Seq(
        c => required("tenant Id", tenantIdValidator)(c.tenantId),
        c => required("terminating user", memberIdValidator)(c.terminatingUser)
      )
    )(terminateTenant)

    if (maybeValidationError.isDefined) {
      Left(maybeValidationError.get)
    } else {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedByOpt = terminateTenant.terminatingUser)
      Right(
        TenantEventResponse(
          TenantTerminated(
            tenantId = terminateTenant.tenantId,
            metaInfo = Some(newMetaInfo)
          )
        )
      )
    }
  }
}
