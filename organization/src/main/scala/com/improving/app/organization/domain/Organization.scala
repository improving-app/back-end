package com.improving.app.organization.domain

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl._
import cats.data.Validated
import cats.implicits.toFoldableOps
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain._
import com.improving.app.organization._
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

object Organization {

  private val log =
    LoggerFactory.getLogger(
      "com.improving.app.organization.domain.Organization"
    )

  val config = ConfigFactory.load()

  def now = java.time.Instant.now()

  val index = config.getInt("organization-service.tags")

  val tags = Vector.tabulate(index)(i => s"organizations-$i")

  trait HasOrganizationId {
    def orgId: Option[OrganizationId]

    def extractOrganizationId: String =
      orgId match {
        case Some(OrganizationId(id, _)) => id
        case other =>
          throw new RuntimeException(s"Unexpected request to extract id $other")
      }
  }

  val OrganizationEntityKey: EntityTypeKey[OrganizationCommand] =
    EntityTypeKey[OrganizationCommand]("Organization")

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[OrganizationCommand] => Behavior[OrganizationCommand] = { entityContext =>
      Organization(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(OrganizationEntityKey)(behaviorFactory))
  }

  final case class OrganizationCommand(
      request: OrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  )

  private def emptyState(id: String): OrganizationState =
    OrganizationState(Some(OrganizationId(id)))

  def apply(
      orgId: String
  ): Behavior[OrganizationCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting Organization {}", orgId)
      EventSourcedBehavior
        .withEnforcedReplies[
          OrganizationCommand,
          OrganizationEvent,
          OrganizationState
        ](
          persistenceId = PersistenceId("Organization", orgId),
          emptyState = emptyState(orgId),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Organization {} stopped", orgId)
        }
        .withTagger(_ => tags.toSet)
    }

  /**
   * @param actingMember
   * @return new MetaInfo with draft status
   */
  def createMetaInfo(actingMember: Option[MemberId]): MetaInfo = {
    val timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
    MetaInfo(
      createdOn = Some(timestamp),
      createdBy = actingMember,
      lastUpdated = Some(timestamp),
      lastUpdatedBy = actingMember,
      currentStatus = OrganizationStatus.ORGANIZATION_STATUS_DRAFT,
      children = Seq.empty[OrganizationId]
    )
  }

  def updateMetaInfo(
      metaInfo: MetaInfo,
      actingMember: Option[MemberId],
      newStatus: Option[OrganizationStatus] = None,
      children: Seq[OrganizationId] = Seq.empty[OrganizationId]
  ) = {
    val timestamp = Timestamp.of(now.getEpochSecond, now.getNano)
    metaInfo.copy(
      lastUpdated = Some(timestamp),
      lastUpdatedBy = actingMember,
      currentStatus = newStatus.getOrElse(metaInfo.currentStatus),
      children = if (children.isEmpty) metaInfo.children else children
    )
  }

  def updateInfo(info: Info, newInfo: Info): Info = {
    info.copy(
      name = newInfo.name,
      shortName = newInfo.shortName.orElse(info.shortName),
      address = newInfo.address.orElse(info.address),
      isPrivate = newInfo.isPrivate,
      url = newInfo.url.orElse(info.url),
      logo = newInfo.logo.orElse(info.logo),
      tenant = newInfo.tenant.orElse(info.tenant)
    )
  }

  /**
   * @param state
   * @param command
   * @return boolean - validate if state transition is valid for the command.
   */
  private def isCommandValidForState(
      state: OrganizationState,
      command: OrganizationRequest
  ): Boolean = {
    command match {
      case _: UpdateOrganizationContactsRequest | _: UpdateOrganizationAccountsRequest | _: UpdateParentRequest |
          _: SuspendOrganizationRequest | _: EditOrganizationInfoRequest | _: RemoveOwnersFromOrganizationRequest |
          _: AddOwnersToOrganizationRequest | _: RemoveMembersFromOrganizationRequest |
          _: AddMembersToOrganizationRequest =>
        state.meta.exists(meta =>
          meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_DRAFT || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_ACTIVE
        )
      case _: UpdateOrganizationStatusRequest =>
        state.meta.exists(meta =>
          !(meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_TERMINATED || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
        )
      case _: TerminateOrganizationRequest => true
//        state.meta.exists(meta =>
//          !(meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_TERMINATED || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
//        )
      case _: ActivateOrganizationRequest =>
        state.meta.exists(meta =>
          meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_DRAFT || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED
        )
      case _: ReleaseOrganizationRequest =>
        state.meta.exists(meta => meta.currentStatus != OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
      case _: GetOrganizationInfoRequest =>
        state.meta.exists(meta =>
          !(meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_TERMINATED || meta.currentStatus == OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
        )
      case _: EstablishOrganizationRequest =>
        state.meta.isEmpty
      case _: GetOrganizationByIdRequest =>
        true
      case other =>
        log.error(s"Invalid Organization Command $other")
        false
    }
  }

  private def getOrganizationInfo(
      orgId: Option[OrganizationId],
      state: OrganizationState,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    log.info(s"getOrganizationInfo: orgId ${orgId.map(_.id)}")
    Effect.reply(replyTo) {
      StatusReply.Success(
        OrganizationInfo(orgId, state.info)
      )
    }
  }

  private def getOrganizationById(
      orgId: Option[OrganizationId],
      state: OrganizationState,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    log.info(s"getOrganizationById: orgId ${orgId.map(_.id)}")
    Effect.reply(replyTo) {
      StatusReply.Success(
        com.improving.app.organization.Organization(
          orgId,
          state.info,
          state.parent,
          state.members,
          state.owners,
          state.contacts,
          state.meta,
          state.status
        )
      )
    }
  }

  private def updateOrganizationContacts(
      uocr: UpdateOrganizationContactsRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationContactsUpdated(
      uocr.orgId,
      uocr.contacts,
      uocr.actingMember
    )
    log.info(
      s"updateOrganizationContacts: UpdateOrganizationContactsRequest $uocr"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }
  private def updateOrganizationAccounts(
      uoar: UpdateOrganizationAccountsRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val info = uoar.info.getOrElse(Info())
    OrganizationValidation.validateInfo(info) match {
      case Validated.Valid(_) => {
        val event = OrganizationAccountsUpdated(
          uoar.orgId,
          uoar.info,
          uoar.actingMember
        )
        log.info(
          s"updateOrganizationAccounts: UpdateOrganizationAccountsRequest $uoar"
        )
        Effect.persist(event).thenReply(replyTo) { _ =>
          StatusReply.Success(OrganizationEventResponse(event))
        }
      }
      case Validated.Invalid(errors) => {
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"updateOrganizationAccounts: Invalid Organization Info with errors: ${errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(",")}"
          )
        }
      }
    }
  }

  private def updateOrganizationStatus(
      uosr: UpdateOrganizationStatusRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationStatusUpdated(
      uosr.orgId,
      uosr.newStatus,
      uosr.actingMember
    )
    log.info(
      s"updateOrganizationStatus: UpdateOrganizationStatusRequest $uosr"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def updateParent(
      upr: UpdateParentRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = ParentUpdated(
      upr.orgId,
      upr.newParent,
      upr.actingMember
    )
    log.info(
      s"updateParent: UpdateParentRequest $upr"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def terminateOrganization(
      tor: TerminateOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationTerminated(
      tor.orgId,
      tor.actingMember
    )
    log.info(
      s"terminateOrganization: TerminateOrganizationRequest $tor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def suspendOrganization(
      sor: SuspendOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationSuspended(
      sor.orgId,
      sor.actingMember
    )
    log.info(
      s"suspendOrganization: SuspendOrganizationRequest $sor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def activateOrganization(
      aor: ActivateOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationActivated(
      aor.orgId,
      aor.actingMember
    )
    log.info(
      s"activateOrganization: ActivateOrganizationRequest $aor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def releaseOrganization(
      ror: ReleaseOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OrganizationReleased(
      ror.orgId,
      ror.actingMember
    )
    log.info(
      s"releaseOrganization: ReleaseOrganizationRequest $ror"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def editOrganizationInfo(
      eoir: EditOrganizationInfoRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val info = eoir.info.getOrElse(Info())
    OrganizationValidation.validateInfo(info) match {
      case Validated.Valid(_) => {
        val event = OrganizationInfoUpdated(
          eoir.orgId,
          eoir.info,
          eoir.actingMember
        )
        log.info(s"editOrganizationInfo: EditOrganizationInfoRequest $eoir")
        Effect.persist(event).thenReply(replyTo) { _ =>
          StatusReply.Success(OrganizationEventResponse(event))
        }
      }
      case Validated.Invalid(errors) => {
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"editOrganizationInfo: Invalid Organization Info with errors: ${errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(",")}"
          )
        }
      }
    }
  }

  private def removeOwnersFromOrganization(
      rfor: RemoveOwnersFromOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OwnersRemovedFromOrganization(
      rfor.orgId,
      rfor.removedOwners,
      rfor.actingMember
    )
    log.info(
      s"removeOwnersFromOrganization: RemoveOwnersFromOrganizationRequest $rfor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def addOwnersToOrganization(
      aoor: AddOwnersToOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = OwnersAddedToOrganization(
      aoor.orgId,
      aoor.newOwners,
      aoor.actingMember
    )
    log.info(s"addOwnersToOrganization: OwnersAddedToOrganization $aoor")
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def removeMembersFromOrganization(
      rfor: RemoveMembersFromOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = MembersRemovedFromOrganization(
      rfor.orgId,
      rfor.removedMembers,
      rfor.actingMember
    )
    log.info(
      s"removeMembersFromOrganization: RemoveMembersFromOrganizationRequest $rfor"
    )
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def addMembersToOrganization(
      amor: AddMembersToOrganizationRequest,
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val event = MembersAddedToOrganization(
      amor.orgId,
      amor.newMembers,
      amor.actingMember
    )
    log.info(s"addMembersToOrganization: AddMembersToOrganizationRequest $amor")
    Effect.persist(event).thenReply(replyTo) { _ =>
      StatusReply.Success(OrganizationEventResponse(event))
    }
  }

  private def establishOrganization(
      eor: EstablishOrganizationRequest,
      orgId: Option[OrganizationId],
      replyTo: ActorRef[StatusReply[OrganizationResponse]]
  ): ReplyEffect[OrganizationEvent, OrganizationState] = {
    val info = eor.info.getOrElse(Info())
    OrganizationValidation.validateInfo(info) match {
      case Validated.Valid(_) => {
        val event = OrganizationEstablished(
          orgId,
          eor.info,
          eor.parent,
          eor.members,
          eor.owners,
          eor.contacts,
          eor.actingMember
        )
        log.info(s"establishOrganization: event $event")
        Effect.persist(event).thenReply(replyTo) { _ =>
          StatusReply.Success(OrganizationEventResponse(event))
        }
      }
      case Validated.Invalid(errors) => {
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"establishOrganization: Invalid Organization Info with errors: ${errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(",")}"
          )
        }
      }
    }
  }

  private val commandHandler: (
      OrganizationState,
      OrganizationCommand
  ) => ReplyEffect[OrganizationEvent, OrganizationState] = { (state, command: OrganizationCommand) =>
    {
      command.request match {
        case cmd if !isCommandValidForState(state, cmd) => {
          Effect.reply(command.replyTo) {
            StatusReply.Error(
              s"Invalid Command ${command.request} for State $state"
            )
          }
        }
        case uocr: UpdateOrganizationContactsRequest =>
          updateOrganizationContacts(uocr, command.replyTo)
        case uoar: UpdateOrganizationAccountsRequest =>
          updateOrganizationAccounts(uoar, command.replyTo)
        case uosr: UpdateOrganizationStatusRequest =>
          updateOrganizationStatus(uosr, command.replyTo)
        case upr: UpdateParentRequest =>
          updateParent(upr, command.replyTo)
        case tor: TerminateOrganizationRequest =>
          terminateOrganization(tor, command.replyTo)
        case sor: SuspendOrganizationRequest =>
          suspendOrganization(sor, command.replyTo)
        case aor: ActivateOrganizationRequest =>
          activateOrganization(aor, command.replyTo)
        case ror: ReleaseOrganizationRequest =>
          releaseOrganization(ror, command.replyTo)
        case eoir: EditOrganizationInfoRequest =>
          editOrganizationInfo(eoir, command.replyTo)
        case rfor: RemoveOwnersFromOrganizationRequest =>
          removeOwnersFromOrganization(rfor, command.replyTo)
        case aoor: AddOwnersToOrganizationRequest =>
          addOwnersToOrganization(aoor, command.replyTo)
        case rfor: RemoveMembersFromOrganizationRequest =>
          removeMembersFromOrganization(rfor, command.replyTo)
        case amor: AddMembersToOrganizationRequest =>
          addMembersToOrganization(amor, command.replyTo)
        case eor: EstablishOrganizationRequest =>
          establishOrganization(eor, state.orgId, command.replyTo)
        case GetOrganizationByIdRequest(orgId, _) =>
          getOrganizationById(orgId, state, command.replyTo)
        case GetOrganizationInfoRequest(orgId, _) =>
          getOrganizationInfo(orgId, state, command.replyTo)
        case other =>
          throw new RuntimeException(s"Invalid/Unhandled request $other")
      }
    }
  }

  private val eventHandler: (OrganizationState, OrganizationEvent) => OrganizationState = { (state, event) =>
    {
      event match {
        case OrganizationEstablished(
              _,
              info,
              parent,
              members,
              owners,
              contacts,
              actingMember,
              _
            ) =>
          state.copy(
            info = info,
            parent = parent,
            members = members,
            owners = owners,
            contacts = contacts,
            meta = Some(createMetaInfo(actingMember)),
            status = OrganizationStatus.ORGANIZATION_STATUS_DRAFT
          )
        case MembersAddedToOrganization(
              _,
              newMembers,
              actingMember,
              _
            ) =>
          state.copy(
            members = state.members ++ newMembers,
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case MembersRemovedFromOrganization(
              _,
              removedMembers,
              actingMember,
              _
            ) =>
          state.copy(
            members = state.members.filterNot(member => removedMembers.contains(member)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OwnersAddedToOrganization(
              _,
              newOwners,
              actingMember,
              _
            ) =>
          state.copy(
            owners = state.owners ++ newOwners,
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OwnersRemovedFromOrganization(
              _,
              removedOwners,
              actingMember,
              _
            ) =>
          state.copy(
            owners = state.owners.filterNot(member => removedOwners.contains(member)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OrganizationInfoUpdated(_, Some(newInfo), actingMember, _) =>
          state.copy(
            info = state.info.map(updateInfo(_, newInfo)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case OrganizationReleased(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_RELEASED,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_RELEASED)
              )
            )
          )
        case OrganizationActivated(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_ACTIVE)
              )
            )
          )
        case OrganizationSuspended(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED)
              )
            )
          )
        case OrganizationTerminated(_, actingMember, _) =>
          state.copy(
            status = OrganizationStatus.ORGANIZATION_STATUS_TERMINATED,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_TERMINATED)
              )
            )
          )
        case ParentUpdated(_, newParent, actingMember, _) =>
          state.copy(
            parent = newParent,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember
              )
            )
          )
        case OrganizationStatusUpdated(
              _,
              newStatus,
              actingMember,
              _
            ) =>
          state.copy(
            status = newStatus,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember,
                Some(newStatus)
              )
            )
          )
        case OrganizationContactsUpdated(
              _,
              contacts,
              actingMember,
              _
            ) =>
          state.copy(
            contacts = contacts,
            meta = state.meta.map(
              updateMetaInfo(
                _,
                actingMember
              )
            )
          )
        case OrganizationAccountsUpdated(
              _,
              Some(newInfo),
              actingMember,
              _
            ) =>
          state.copy(
            info = state.info.map(updateInfo(_, newInfo)),
            meta = state.meta.map(updateMetaInfo(_, actingMember))
          )
        case other =>
          throw new RuntimeException(s"Invalid/Unhandled event $other")
      }
    }
  }
}
