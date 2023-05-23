package com.improving.app.tenant.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{Address, Contact, EditableAddress, EditableContact, MemberId, OrganizationId}
import com.improving.app.common.errors._
import com.improving.app.tenant.domain.Validation.completeEditableTenantInfoValidator
import com.improving.app.tenant.domain.util.infoFromEditableInfo

import java.time.Instant

object Tenant {
  val TypeKey: EntityTypeKey[TenantCommand] = EntityTypeKey[TenantCommand]("Tenant")

  case class TenantCommand(request: TenantRequest, replyTo: ActorRef[StatusReply[TenantEnvelope]])

  sealed trait TenantState

  private case object UninitializedTenant extends TenantState

  private trait EstablishedTenant extends TenantState {
    def metaInfo: TenantMetaInfo
  }

  sealed private trait InitializedTenant extends EstablishedTenant {
    val info: TenantInfo
    val metaInfo: TenantMetaInfo
  }

  sealed private trait InactiveTenant extends EstablishedTenant {
    override val metaInfo: TenantMetaInfo
  }

  private case class DraftTenant(info: EditableTenantInfo, metaInfo: TenantMetaInfo) extends InactiveTenant

  private case class TerminatedTenant(metaInfo: TenantMetaInfo) extends TenantState

  private case class ActiveTenant(info: TenantInfo, metaInfo: TenantMetaInfo) extends InitializedTenant

  private case class SuspendedTenant(info: TenantInfo, metaInfo: TenantMetaInfo, suspensionReason: String)
      extends InitializedTenant
      with InactiveTenant

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
        case inactiveTenant: InactiveTenant =>
          command.request match {
            case _: EstablishTenant  => Left(StateError("Tenant is already established"))
            case x: ActivateTenant   => activateTenant(inactiveTenant, x)
            case x: SuspendTenant    => suspendTenant(inactiveTenant, x)
            case x: EditInfo         => editInfo(inactiveTenant, x)
            case x: GetOrganizations => getOrganizations(x, Some(inactiveTenant))
            case x: TerminateTenant  => terminateTenant(inactiveTenant, x)
            case _                   => Left(StateError("Command is not supported"))
          }
        case establishedState: EstablishedTenant =>
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
                case _: EstablishTenant  => Left(StateError(""))
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
              case UninitializedTenant =>
                DraftTenant(info = e.tenantInfo.getOrElse(EditableTenantInfo.defaultInstance), metaInfo = e.metaInfo)
              case x => x
            }
          case e: TenantActivated =>
            state match {
              case x: DraftTenant     => ActiveTenant(infoFromEditableInfo(x.info), e.metaInfo)
              case x: SuspendedTenant => ActiveTenant(x.info, e.metaInfo)
              case _                  => state
            }
          case e: TenantSuspended =>
            state match {
              case x: InitializedTenant => SuspendedTenant(x.info, e.metaInfo, e.suspensionReason)
              case UninitializedTenant  => UninitializedTenant
              case _                    => state
            }
          case e: InfoEdited =>
            state match {
              case x: DraftTenant => x.copy(info = e.newInfo.getEditable, metaInfo = e.metaInfo)
              case x: ActiveTenant =>
                x.copy(info = e.newInfo.getInfo, metaInfo = e.metaInfo)
              case x: SuspendedTenant  => x.copy(info = e.newInfo.getInfo, metaInfo = e.metaInfo)
              case UninitializedTenant => UninitializedTenant
              case _: TerminatedTenant => state
            }
          case _: TenantTerminated =>
            state match {
              case x: EstablishedTenant => TerminatedTenant(x.metaInfo)
              case _                    => state
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
          tenantInfo = establishTenant.tenantInfo
        )
      )
    )
  }

  private def activateTenant(
      state: EstablishedTenant,
      activateTenant: ActivateTenant,
  ): Either[Error, TenantEnvelope] = {
    val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = activateTenant.activatingUser)
    state match {
      case draft: DraftTenant =>
        val errorsOpt = completeEditableTenantInfoValidator(draft.info)
        errorsOpt match {
          case None =>
            Right(
              TenantEventResponse(
                TenantActivated(
                  tenantId = activateTenant.tenantId,
                  metaInfo = newMetaInfo
                )
              )
            )
          case Some(error) => Left(error)
        }
      case _: InitializedTenant =>
        Right(
          TenantEventResponse(
            TenantActivated(
              tenantId = activateTenant.tenantId,
              metaInfo = newMetaInfo
            )
          )
        )
    }
  }

  private def suspendTenant(
      state: EstablishedTenant,
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
      state: Tenant.EstablishedTenant,
      editInfoCommand: EditInfo,
  ): Either[Error, TenantEnvelope] = state match {
    case draftState: DraftTenant =>
      val infoToUpdate = editInfoCommand.infoToUpdate
      val stateInfo = draftState.info
      val stateAddress = stateInfo.getAddress
      val stateContact = stateInfo.getPrimaryContact

      val updatedInfo = EditableTenantInfo(
        name = infoToUpdate.name.orElse(stateInfo.name),
        address = infoToUpdate.address match {
          case Some(EditableAddress(line1, line2, city, stateProvince, country, postalCode, _)) =>
            Some(
              EditableAddress(
                line1.orElse(stateAddress.line1),
                line2.orElse(stateAddress.line2),
                city.orElse(stateAddress.city),
                stateProvince.orElse(stateAddress.stateProvince),
                country.orElse(stateAddress.country),
                postalCode.orElse(stateAddress.postalCode)
              )
            )
          case None => stateInfo.address
        },
        primaryContact = infoToUpdate.primaryContact match {
          case Some(EditableContact(firstName, lastName, email, phone, username, _)) =>
            Some(
              EditableContact(
                firstName.orElse(stateContact.firstName),
                lastName.orElse(stateContact.lastName),
                email.orElse(stateContact.emailAddress),
                phone.orElse(stateContact.phone),
                username.orElse(stateContact.userName)
              )
            )
          case None => stateInfo.primaryContact
        },
        organizations = infoToUpdate.organizations.orElse(stateInfo.organizations)
      )

      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = editInfoCommand.editingUser)

      Right(
        TenantEventResponse(
          InfoEdited(
            tenantId = editInfoCommand.tenantId,
            metaInfo = newMetaInfo,
            oldInfo = TenantInfoOrEditable(TenantInfoOrEditable.Value.Editable(draftState.info)),
            newInfo = TenantInfoOrEditable(TenantInfoOrEditable.Value.Editable(updatedInfo))
          )
        )
      )
    case initializedState: InitializedTenant =>
      val infoToUpdate = editInfoCommand.infoToUpdate
      val stateInfo = initializedState.info
      val stateAddress = stateInfo.address
      val stateContact = stateInfo.primaryContact

      val updatedInfo = TenantInfo(
        name = infoToUpdate.name.getOrElse(stateInfo.name),
        address = infoToUpdate.address match {
          case Some(EditableAddress(line1, line2, city, stateProvince, country, postalCode, _)) =>
            Address(
              line1.getOrElse(stateAddress.line1),
              line2.orElse(stateAddress.line2),
              city.getOrElse(stateAddress.city),
              stateProvince.getOrElse(stateAddress.stateProvince),
              country.getOrElse(stateAddress.country),
              postalCode.orElse(stateAddress.postalCode)
            )
          case None => stateInfo.address
        },
        primaryContact = infoToUpdate.primaryContact match {
          case Some(EditableContact(firstName, lastName, email, phone, username, _)) =>
            Contact(
              firstName.getOrElse(stateContact.firstName),
              lastName.getOrElse(stateContact.lastName),
              email.orElse(stateContact.emailAddress),
              phone.orElse(stateContact.phone),
              username.getOrElse(stateContact.userName)
            )
          case None => stateInfo.primaryContact
        },
        organizations = infoToUpdate.organizations.getOrElse(stateInfo.organizations)
      )

      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = editInfoCommand.editingUser)

      Right(
        TenantEventResponse(
          InfoEdited(
            tenantId = editInfoCommand.tenantId,
            metaInfo = newMetaInfo,
            oldInfo = TenantInfoOrEditable(TenantInfoOrEditable.Value.Info(initializedState.info)),
            newInfo = TenantInfoOrEditable(TenantInfoOrEditable.Value.Info(updatedInfo))
          )
        )
      )
  }

  private def getOrganizations(
      getOrganizationsQuery: GetOrganizations,
      stateOpt: Option[Tenant.EstablishedTenant] = None
  ): Either[Error, TenantEnvelope] = {
    Right(
      TenantDataResponse(
        TenantOrganizationData(
          organizations = TenantOrganizationList(stateOpt.fold[Seq[OrganizationId]](Seq.empty) {
            case draft: DraftTenant             => draft.info.getOrganizations.value
            case initialized: InitializedTenant => initialized.info.organizations.value
          })
        )
      )
    )
  }

  private def terminateTenant(
      state: EstablishedTenant,
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
