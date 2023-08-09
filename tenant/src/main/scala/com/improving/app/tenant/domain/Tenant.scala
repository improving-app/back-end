package com.improving.app.tenant.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.{Counter, Tracer}
import com.improving.app.common.domain.{Address, Contact, EditableAddress, EditableContact, MemberId, OrganizationId}
import com.improving.app.common.errors._
import com.improving.app.tenant.domain.Validation.{draftTransitionTenantInfoValidator, tenantCommandValidator, tenantRequestValidator}
import com.improving.app.tenant.domain.util.EditableInfoUtil
import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.api.common.Attributes

import java.time.Instant

object Tenant extends StrictLogging {
  val TypeKey: EntityTypeKey[TenantRequestEnvelope] = EntityTypeKey[TenantRequestEnvelope]("Tenant")

  // Counter metric for tenants
  private val totalTenants: Counter =
    Counter("total-tenants", "", "The total number of tenants, active or suspended.", "each")

  // Counter metric for active tenants
  private val activeTenants: Counter =
    Counter("active-tenants", "", "The total number of tenants currently active.", "each")

  // Tracer for tracing call chains
  private val tracer = Tracer("Tenant")

  case class TenantRequestEnvelope(request: TenantRequestPB, replyTo: ActorRef[StatusReply[TenantResponse]])

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

  def apply(persistenceId: PersistenceId): Behavior[TenantRequestEnvelope] = {
    Behaviors.setup(context =>
      EventSourcedBehavior[TenantRequestEnvelope, TenantResponse, TenantState](
        persistenceId = persistenceId,
        emptyState = UninitializedTenant,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    )
  }

  private val commandHandler: (TenantState, TenantRequestEnvelope) => ReplyEffect[TenantResponse, TenantState] = {
    (state, envelope) => {
      val span = tracer.startSpan("commandHandler")
      try {
        logger.debug(s"Handling command ${envelope.request.asMessage.toProtoString}")
        val requestErrors: Either[ReplyEffect[TenantResponse, TenantState], TenantRequestPB with TenantRequest] =
          envelope.request match {
            case r: TenantRequest => Right(r)
            case _ => Left(Effect.reply(envelope.replyTo)(StatusReply.Error("Message was not a TenantRequest")))
          }

        requestErrors.map(tenantRequestValidator) match {
          case Right(None) =>
            def getCommandErrors(
                                  errorsOrResponse: Either[Error, TenantResponse]
                                ): Either[Error, TenantResponse] =
              envelope.request match {
                case r: TenantCommand =>
                  tenantCommandValidator(r) match {
                    case None => errorsOrResponse
                    case Some(err: ValidationError) => Left(err)
                  }
                case _ => Left(ValidationError("Message was not a TenantRequest"))
              }

            val result: Either[Error, TenantResponse] = state match {
              case UninitializedTenant =>
                envelope.request match {
                  case x: EstablishTenant => getCommandErrors(establishTenant(x))
                  case _: GetOrganizations => getOrganizations()
                  case _ => Left(StateError("Tenant is not established"))
                }
              case draftState: DraftTenant =>
                envelope.request match {
                  case _: EstablishTenant => Left(StateError("Tenant is already established"))
                  case x: ActivateTenant => getCommandErrors(activateTenant(draftState, x))
                  case x: SuspendTenant => getCommandErrors(suspendTenant(draftState, x))
                  case x: EditInfo => getCommandErrors(editInfo(draftState, x))
                  case _: GetOrganizations => getOrganizations(Some(draftState))
                  case x: TerminateTenant => getCommandErrors(terminateTenant(draftState, x))
                  case _ => Left(StateError("Command is not supported"))
                }
              case establishedState: EstablishedTenant =>
                establishedState match {
                  case activeTenantState: ActiveTenant =>
                    envelope.request match {
                      case _: EstablishTenant => Left(StateError("Tenant is already established"))
                      case _: ActivateTenant =>
                        Left(StateError("Active tenants may not transition to the Active state"))
                      case x: SuspendTenant => getCommandErrors(suspendTenant(establishedState, x))
                      case x: EditInfo => getCommandErrors(editInfo(establishedState, x))
                      case _: GetOrganizations => getOrganizations(Some(activeTenantState))
                      case x: TerminateTenant => getCommandErrors(terminateTenant(establishedState, x))
                      case _ => Left(StateError("Command is not supported"))
                    }
                  case suspendedTenantState: SuspendedTenant =>
                    envelope.request match {
                      case _: EstablishTenant => Left(StateError("Tenant is already established"))
                      case x: ActivateTenant => getCommandErrors(activateTenant(establishedState, x))
                      case x: SuspendTenant => getCommandErrors(suspendTenant(establishedState, x))
                      case x: EditInfo => getCommandErrors(editInfo(establishedState, x))
                      case _: GetOrganizations => getOrganizations(Some(suspendedTenantState))
                      case x: TerminateTenant => getCommandErrors(terminateTenant(establishedState, x))
                      case _ => Left(StateError("Command is not supported"))
                    }
                }
              case _: TerminatedTenant =>
                envelope.request match {
                  case _ => Left(StateError("Command not allowed in Terminated state"))
                }
            }

            result match {
              case Left(error) => Effect.reply(envelope.replyTo)(StatusReply.Error(error.message))
              case Right(response) =>
                response match {
                  case _: TenantDataResponse =>
                    Effect.reply(envelope.replyTo) {
                      StatusReply.Success(response)
                    }
                  case _: TenantEventResponse =>
                    Effect.persist(response).thenReply(envelope.replyTo) { _ => StatusReply.Success(response) }
                  case _ =>
                    Effect.reply(envelope.replyTo)(
                      StatusReply.Error(s"${response.productPrefix} is not a supported member response")
                    )
                }
            }
          case Right(Some(errors)) => Effect.reply(envelope.replyTo)(StatusReply.Error(errors.message))
          case Left(r) => r
        }
      } finally span.end()
    }
  }

  private val eventHandler: (TenantState, TenantResponse) => TenantState = { (state, response) => {
    val span = tracer.startSpan("eventHandler")
    try {
      response match {
        case event: TenantEventResponse =>
          event.tenantEvent match {
            case e: TenantEstablished =>
              state match {
                case UninitializedTenant =>
                  DraftTenant(info = e.tenantInfo.getOrElse(EditableTenantInfo.defaultInstance), metaInfo = e.getMetaInfo)
                case x => x
              }
            case e: TenantActivated =>
              state match {
                case x: DraftTenant => ActiveTenant(x.info.toInfo, e.getMetaInfo)
                case x: SuspendedTenant => ActiveTenant(x.info, e.getMetaInfo)
                case x => x
              }
            case e: TenantSuspended =>
              state match {
                case x: DraftTenant => SuspendedTenant(x.info.toInfo, e.getMetaInfo, e.suspensionReason)
                case x: InitializedTenant => SuspendedTenant(x.info, e.getMetaInfo, e.suspensionReason)
                case x => x
              }
            case e: TenantInfoEdited =>
              state match {
                case x: DraftTenant => x.copy(info = e.getNewInfo.getEditable, metaInfo = e.getMetaInfo)
                case x: ActiveTenant =>
                  x.copy(info = e.getNewInfo.getInfo, metaInfo = e.getMetaInfo)
                case x: SuspendedTenant => x.copy(info = e.getNewInfo.getInfo, metaInfo = e.getMetaInfo)
                case UninitializedTenant => UninitializedTenant
                case _: TerminatedTenant => state
              }
            case _: TenantTerminated =>
              state match {
                case x: EstablishedTenant => TerminatedTenant(x.metaInfo)
                case _ => state
              }
            case _ => state
          }
        case _ => state
      }
    } finally span.end()
  }
  }

  private def updateMetaInfo(metaInfo: TenantMetaInfo, lastUpdatedBy: Option[MemberId]): TenantMetaInfo = {
    metaInfo.copy(lastUpdatedBy = lastUpdatedBy, lastUpdated = Some(Timestamp(Instant.now())))
  }

  private def establishTenant(establishTenant: EstablishTenant): Either[Error, TenantResponse] = {
    val span = tracer.startSpan("establishTenant")
    try {
      val now = Instant.now()

      val newMetaInfo = TenantMetaInfo(
        createdBy = establishTenant.onBehalfOf,
        createdOn = Some(Timestamp(now)),
        lastUpdated = Some(Timestamp(now)),
        lastUpdatedBy = establishTenant.onBehalfOf,
        state = TenantState.TENANT_STATE_ACTIVE
      )
      totalTenants.incr()
      Right(
        TenantEventResponse(
          TenantEstablished(
            tenantId = establishTenant.tenantId,
            metaInfo = Some(newMetaInfo),
            tenantInfo = establishTenant.tenantInfo
          )
        )
      )
    } finally span.end()
  }

  private def activateTenant(
      state: EstablishedTenant,
      activateTenant: ActivateTenant,
  ): Either[Error, TenantResponse] = {
    val span = tracer.startSpan("establishTenant")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = activateTenant.onBehalfOf)
      state match {
        case draft: DraftTenant =>
          val errorsOpt = draftTransitionTenantInfoValidator(draft.info)
          errorsOpt match {
            case None =>
              activeTenants.incr()
              Right(
                TenantEventResponse(
                  TenantActivated(
                    tenantId = activateTenant.tenantId,
                    metaInfo = Some(newMetaInfo)
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
                metaInfo = Some(newMetaInfo)
              )
            )
          )
      }
    } finally span.end()
  }

  private def suspendTenant(
      state: EstablishedTenant,
      suspendTenant: SuspendTenant,
  ): Either[Error, TenantResponse] = {
    val span = tracer.startSpan("establishTenant")
    try {
      val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = suspendTenant.onBehalfOf)
      activeTenants.decr()
      Right(
        TenantEventResponse(
          TenantSuspended(
            tenantId = suspendTenant.tenantId,
            metaInfo = Some(newMetaInfo),
            suspensionReason = suspendTenant.suspensionReason
          )
        )
      )
    } finally span.end()
  }

  private def editInfo(
      state: Tenant.EstablishedTenant,
      editInfoCommand: EditInfo,
  ): Either[Error, TenantResponse] = {
    val span = tracer.startSpan("establishTenant")
    try {
      state match {
        case draftState: DraftTenant =>
          val infoToUpdate = editInfoCommand.infoToUpdate
          val stateInfo = draftState.info
          val stateAddress = stateInfo.address
          val stateContact = stateInfo.primaryContact

          val updatedInfo = infoToUpdate match {
            case Some(info) =>
              EditableTenantInfo(
                name = info.name.orElse(stateInfo.name),
                address = info.address match {
                  case Some(EditableAddress(line1, line2, city, stateProvince, country, postalCode, _)) =>
                    Some(
                      EditableAddress(
                        line1.orElse(stateAddress.flatMap(_.line1)),
                        line2.orElse(stateAddress.flatMap(_.line2)),
                        city.orElse(stateAddress.flatMap(_.city)),
                        stateProvince.orElse(stateAddress.flatMap(_.stateProvince)),
                        country.orElse(stateAddress.flatMap(_.country)),
                        postalCode.orElse(stateAddress.flatMap(_.postalCode))
                      )
                    )
                  case None => stateInfo.address
                },
                primaryContact = info.primaryContact match {
                  case Some(EditableContact(firstName, lastName, email, phone, username, _)) =>
                    Some(
                      EditableContact(
                        firstName.orElse(stateContact.flatMap(_.firstName)),
                        lastName.orElse(stateContact.flatMap(_.lastName)),
                        email.orElse(stateContact.flatMap(_.emailAddress)),
                        phone.orElse(stateContact.flatMap(_.phone)),
                        username.orElse(stateContact.flatMap(_.userName))
                      )
                    )
                  case None => stateInfo.primaryContact
                },
                organizations = info.organizations.orElse(stateInfo.organizations)
              )
            case None => stateInfo
          }

          val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = editInfoCommand.onBehalfOf)

          Right(
            TenantEventResponse(
              TenantInfoEdited(
                tenantId = editInfoCommand.tenantId,
                metaInfo = Some(newMetaInfo),
                newInfo = Some(TenantInfoOrEditable(TenantInfoOrEditable.Value.Editable(updatedInfo)))
              )
            )
          )
        case initializedState: InitializedTenant =>
          val infoToUpdate = editInfoCommand.infoToUpdate
          val stateInfo = initializedState.info
          val stateAddress = stateInfo.getAddress
          val stateContact = stateInfo.getPrimaryContact

          val updatedInfo = infoToUpdate match {
            case Some(info) =>
              TenantInfo(
                name = info.name.getOrElse(stateInfo.name),
                address = info.address match {
                  case Some(EditableAddress(line1, line2, city, stateProvince, country, postalCode, _)) =>
                    Some(
                      Address(
                        line1.getOrElse(stateAddress.line1),
                        line2.orElse(stateAddress.line2),
                        city.getOrElse(stateAddress.city),
                        stateProvince.getOrElse(stateAddress.stateProvince),
                        country.getOrElse(stateAddress.country),
                        postalCode.orElse(stateAddress.postalCode)
                      )
                    )
                  case None => stateInfo.address
                },
                primaryContact = info.primaryContact match {
                  case Some(EditableContact(firstName, lastName, email, phone, username, _)) =>
                    Some(
                      Contact(
                        firstName.getOrElse(stateContact.firstName),
                        lastName.getOrElse(stateContact.lastName),
                        email.orElse(stateContact.emailAddress),
                        phone.orElse(stateContact.phone),
                        username.getOrElse(stateContact.userName)
                      )
                    )
                  case None => stateInfo.primaryContact
                },
                organizations = info.organizations.orElse(stateInfo.organizations)
              )
            case None => stateInfo
          }

          val newMetaInfo = updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = editInfoCommand.onBehalfOf)

          Right(
            TenantEventResponse(
              TenantInfoEdited(
                tenantId = editInfoCommand.tenantId,
                metaInfo = Some(newMetaInfo),
                newInfo = Some(TenantInfoOrEditable(TenantInfoOrEditable.Value.Info(updatedInfo)))
              )
            )
          )
      }
    } finally span.end()
  }

  private def getOrganizations(
      establishedInfoOpt: Option[Tenant.EstablishedTenant] = None
  ): Either[Error, TenantResponse] = {
    val span = tracer.startSpan("establishTenant")
    try {
      Right(
        TenantDataResponse(
          TenantOrganizationData(
            organizations = Some(TenantOrganizationList(establishedInfoOpt.fold[Seq[OrganizationId]](Seq.empty) {
              case draft: DraftTenant => draft.info.getOrganizations.value
              case initialized: InitializedTenant =>
                initialized.info.organizations.getOrElse(TenantOrganizationList.defaultInstance).value
            }))
          )
        )
      )
    } finally span.end()
  }

  private def terminateTenant(
      state: EstablishedTenant,
      terminateTenant: TerminateTenant,
  ): Either[Error, TenantResponse] = {
    val span = tracer.startSpan("establishTenant")
    try {
      val newMetaInfo =
        updateMetaInfo(metaInfo = state.metaInfo, lastUpdatedBy = terminateTenant.onBehalfOf)
      totalTenants.decr()
      Right(
        TenantEventResponse(
          TenantTerminated(
            tenantId = terminateTenant.tenantId,
            metaInfo = Some(newMetaInfo)
          )
        )
      )
    } finally span.end()
  }
}
