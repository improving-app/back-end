package com.improving.app.tenant.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.common.errors._
import com.improving.app.tenant.domain.util.infoFromEditableInfo

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
              case UninitializedTenant => ActiveTenant(info = e.tenantInfo, metaInfo = e.metaInfo)
              case _: ActiveTenant     => state
              case _: SuspendedTenant  => state
              case _: TerminatedTenant => state
            }
          case e: TenantActivated =>
            state match {
              case _: ActiveTenant     => state // tenant cannot have TenantActivated in Active state
              case x: SuspendedTenant  => ActiveTenant(x.info, e.metaInfo)
              case UninitializedTenant => UninitializedTenant
              case _: TerminatedTenant => state
            }
          case e: TenantSuspended =>
            state match {
              case x: ActiveTenant     => SuspendedTenant(x.info, e.metaInfo, e.suspensionReason)
              case x: SuspendedTenant  => SuspendedTenant(x.info, e.metaInfo, e.suspensionReason)
              case UninitializedTenant => UninitializedTenant
              case _: TerminatedTenant => state
            }
          case e: InfoEdited =>
            state match {
              case x: ActiveTenant     => x.copy(info = e.newInfo, metaInfo = e.metaInfo)
              case x: SuspendedTenant  => x.copy(info = e.newInfo, metaInfo = e.metaInfo)
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

  private def updateMetaInfo(metaInfo: TenantMetaInfo, lastUpdatedBy: MemberId): TenantMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedBy, lastUpdated = Timestamp(Instant.now()))
  }

  private def establishTenant(establishTenant: EstablishTenant): Either[Error, TenantEnvelope] = {
    val tenantInfo = establishTenant.tenantInfo.get

    val now = Instant.now()

    val newMetaInfo = TenantMetaInfo(
      createdBy = establishTenant.establishingUser,
      createdOn = Timestamp(now),
      lastUpdated = Timestamp(now),
      lastUpdatedBy = establishTenant.establishingUser,
      state = TenantState.TENANT_STATE_ACTIVE
    )

    Right(
      TenantEventResponse(
        TenantEstablished(
          tenantId = establishTenant.tenantId,
          metaInfo = newMetaInfo,
          tenantInfo = tenantInfo
        )
      )
    )
  }

  private def activateTenant(
      state: EstablishedTenantState,
      activateTenant: ActivateTenant,
  ): Either[Error, TenantEnvelope] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = activateTenant.activatingUser)
    Right(
      TenantEventResponse(
        TenantActivated(
          tenantId = activateTenant.tenantId,
          metaInfo = newMetaInfo
        )
      )
    )
  }

  private def suspendTenant(
      state: EstablishedTenantState,
      suspendTenant: SuspendTenant,
  ): Either[Error, TenantEnvelope] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = suspendTenant.suspendingUser)
    Right(
      TenantEventResponse(
        TenantSuspended(
          tenantId = suspendTenant.tenantId,
          metaInfo = newMetaInfo,
          suspensionReason = suspendTenant.suspensionReason
        )
      )
    )
  }

  private def editInfo(
      state: Tenant.EstablishedTenantState,
      editInfoCommand: EditInfo,
  ): Either[Error, TenantEnvelope] = {
    val infoToUpdate = editInfoCommand.infoToUpdate
    val stateInfo = state.info
    val newInfo = infoFromEditableInfo(infoToUpdate, stateInfo)

    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = editInfoCommand.editingUser)

    Right(
      TenantEventResponse(
        InfoEdited(
          tenantId = editInfoCommand.tenantId,
          metaInfo = newMetaInfo,
          oldInfo = state.info,
          newInfo = newInfo
        )
      )
    )
  }

  private def getOrganizations(
      getOrganizationsQuery: GetOrganizations,
      stateOpt: Option[Tenant.EstablishedTenantState] = None
  ): Either[Error, TenantEnvelope] = {
    Right(
      TenantDataResponse(
        TenantOrganizationData(
          organizations = TenantOrganizationList(stateOpt.fold[Seq[OrganizationId]](Seq.empty) {
            _.info.organizations.value
          })
        )
      )
    )
  }

  private def terminateTenant(
      state: EstablishedTenantState,
      terminateTenant: TerminateTenant,
  ): Either[Error, TenantEnvelope] = {
    val newMetaInfo =
      updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = terminateTenant.terminatingUser)
    Right(
      TenantEventResponse(
        TenantTerminated(
          tenantId = terminateTenant.tenantId,
          metaInfo = newMetaInfo
        )
      )
    )
  }
}
