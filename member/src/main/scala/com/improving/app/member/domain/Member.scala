package com.improving.app.member.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import cats.data
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toFoldableOps
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.MemberId
import com.improving.app.member.domain.MemberState._
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}

object Member extends StrictLogging {

  private val clock: Clock = Clock.systemDefaultZone()

  val MemberEntityKey: EntityTypeKey[MemberCommand] = EntityTypeKey[MemberCommand]("Member")

  //Command wraps the request type
  final case class MemberCommand(request: MemberRequest, replyTo: ActorRef[StatusReply[MemberResponse]])

  private def emptyState(): MemberState = {
    DraftMemberState(
      requiredInfo = RequiredDraftInfo(),
      optionalInfo = OptionalDraftInfo(),
      meta = MemberMetaInfo()
    )
  }

  sealed trait MemberState

  case class DraftMemberState(requiredInfo: RequiredDraftInfo, optionalInfo: OptionalDraftInfo, meta: MemberMetaInfo) extends MemberState
  case class RegisteredMemberState(info: MemberInfo, meta: MemberMetaInfo) extends MemberState
  case class TerminatedMemberState(lastMeta: MemberMetaInfo) extends MemberState

  trait HasMemberId {
    def memberId: Option[MemberId]

    def extractMemberId: String =
      memberId match {
        case Some(MemberId(id, _)) => id
        case other                 => throw new RuntimeException(s"Unexpected request to extract id $other")
      }
  }

  def apply(entityTypeHint: String, memberId: String): Behavior[MemberCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting Member {}", memberId)
      EventSourcedBehavior
        .withEnforcedReplies[MemberCommand, MemberEvent, MemberState](
          persistenceId = PersistenceId(entityTypeHint, memberId),
          emptyState = emptyState(),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        //.withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Member {} stopped", memberId)
        }
    }

  /**
   * State Diagram
   *
   * Initial -> Active <-> Inactive <-> Suspended -> Terminated
   */
  //CommandHandler
  private val commandHandler: (MemberState, MemberCommand) => ReplyEffect[MemberEvent, MemberState] = {
    (state, command) =>
      command.request match {
        case registerMemberCommand: RegisterMember => registerMember(state, registerMemberCommand, command.replyTo)
        case activateMemberCommand: ActivateMember => activateMember(state, activateMemberCommand, command.replyTo)
        case suspendMemberCommand: SuspendMember => suspendMember(state, suspendMemberCommand, command.replyTo)
        case terminateMemberCommand: TerminateMember => terminateMember(state, terminateMemberCommand, command.replyTo)
        case editMemberInfoCommand: EditMemberInfo => editMemberInfo(state,  editMemberInfoCommand, command.replyTo)
        case getMemberInfoCommand: GetMemberInfo => getMemberInfo(state, getMemberInfoCommand, command.replyTo)
        case _ => useErrorStatusReply(command.replyTo, s"Invalid Command ${command.request}")
      }
  }

  //EventHandler
  private val eventHandler: (MemberState, MemberEvent) => MemberState = { (state, event) =>
    event match {
      case memberRegisteredEvent: MemberRegistered =>
        state match {
          case x: DraftMemberState =>
            x.copy(
              requiredInfo = x.requiredInfo.copy(
                contact = memberRegisteredEvent.memberInfo.get.contact,
                handle = memberRegisteredEvent.memberInfo.get.handle,
                avatarUrl = memberRegisteredEvent.memberInfo.get.avatarUrl,
                firstName = memberRegisteredEvent.memberInfo.get.firstName,
                lastName = memberRegisteredEvent.memberInfo.get.lastName,
                tenant = memberRegisteredEvent.memberInfo.get.tenant
              ),
              optionalInfo = x.optionalInfo.copy(
                notificationPreference = memberRegisteredEvent.memberInfo.get.notificationPreference,
                organizationMembership = memberRegisteredEvent.memberInfo.get.organizationMembership
              ),
              meta = memberRegisteredEvent.meta.get
            )
          case _: RegisteredMemberState => state
          case _: TerminatedMemberState => state
        }

      case memberActivatedEvent: MemberActivated =>
        state match {
          case DraftMemberState(requiredInfo, optionalInfo, _) => RegisteredMemberState(
            info = createMemberInfoFromDraftState(requiredInfo, optionalInfo),
            meta = memberActivatedEvent.meta.get
          )
          case x: RegisteredMemberState =>
            if (x.meta.currentState.isMemberStatusSuspended) {
              x.copy(meta = memberActivatedEvent.meta.get)
            } else {
              state
            }
          case _: TerminatedMemberState => state
        }

      case memberSuspendedEvent: MemberSuspended =>
        state match {
          case _: DraftMemberState => state
          case x: RegisteredMemberState => x.copy(meta = memberSuspendedEvent.meta.get)
          case _: TerminatedMemberState => state
        }

      case memberTerminatedEvent: MemberTerminated =>
        state match {
          case _: DraftMemberState => state
          case _: RegisteredMemberState => TerminatedMemberState(lastMeta = memberTerminatedEvent.lastMeta.get)
          case _: TerminatedMemberState => state
        }

      case memberInfoEdited: MemberInfoEdited =>
        state match {
          case _: DraftMemberState =>
            val info = memberInfoEdited.memberInfo.get
            DraftMemberState(
              requiredInfo = RequiredDraftInfo(
                contact = info.contact,
                handle = info.handle,
                avatarUrl = info.avatarUrl,
                firstName = info.firstName,
                lastName = info.lastName,
                tenant = info.tenant
              ),
              optionalInfo = OptionalDraftInfo(
                notificationPreference = info.notificationPreference,
                organizationMembership = info.organizationMembership
              ),
              meta = memberInfoEdited.meta.get
            )
          case _: RegisteredMemberState =>
            RegisteredMemberState(
              info = memberInfoEdited.memberInfo.get,
              meta = memberInfoEdited.meta.get
            )
          case _: TerminatedMemberState => state
        }

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled event $other")
    }
  }

  private def registerMemberLogic(
    meta: MemberMetaInfo,
    registerMemberCommand: RegisterMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    if (meta.createdBy.isDefined) {
      useErrorStatusReply(replyTo, s"Member has already been registered.")
    } else {
      val now = Some(Timestamp(Instant.now(clock)))
      val newMeta = meta.copy(
        lastModifiedOn = now,
        lastModifiedBy = registerMemberCommand.registeringMember,
        createdOn = now,
        createdBy = registerMemberCommand.registeringMember,
        currentState = MEMBER_STATUS_DRAFT
      )
      val event =
        MemberRegistered(registerMemberCommand.memberId, registerMemberCommand.memberInfo, Some(newMeta))
      Effect.persist(event).thenReply(replyTo) { _ =>
        val res = StatusReply.Success(MemberEventResponse(event))
        logger.info(s"registerMember: $res")
        res
      }
    }
  }

  private def registerMember(
    state: MemberState,
    registerMemberCommand: RegisterMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateRegisterMember(registerMemberCommand) match {
      case Valid(_) =>
        state match {
          case DraftMemberState(_, _, meta) => registerMemberLogic(meta, registerMemberCommand, replyTo)
          case _: RegisteredMemberState => useErrorStatusReply(replyTo, s"Member has already been registered.")
          case _: TerminatedMemberState => useTerminatedStateErrorStatusReply(replyTo)
        }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "RegisterMember")
    }
  }

  private def activateMemberLogic(
    info: MemberInfo,
    meta: MemberMetaInfo,
    activateMemberCommand: ActivateMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    if (meta.createdBy.isEmpty) {
      useErrorStatusReply(replyTo, s"A member not registered cannot be activated")
    } else if (meta.currentState.isMemberStatusActive) {
      useErrorStatusReply(replyTo, s"Member has already been activated")
    } else {
      MemberValidation.validateMemberInfo(info) match {
        case Valid(_) =>
          val newMeta = meta.copy(
            lastModifiedBy = activateMemberCommand.activatingMember,
            lastModifiedOn = Some(Timestamp(Instant.now(clock))),
            currentState = MEMBER_STATUS_ACTIVE
          )
          val event = MemberActivated(activateMemberCommand.memberId, Some(newMeta))

          Effect
            .persist(event)
            .thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }
        case Invalid(errors) =>
          val message = s"Member info is not sufficiently filled out to activate member: ${
            errors.map {
              _.errorMessage
            }.toList.mkString(", ")
          }"
          useErrorStatusReply(replyTo, message)
      }
    }
  }

  private def activateMember(
    state: MemberState,
    activateMemberCommand: ActivateMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateActivateMemberCommand(activateMemberCommand) match {
      case Valid(_) =>
        state match {
          case DraftMemberState(requiredInfo, optionalInfo, meta) => {
            val info = createMemberInfoFromDraftState(requiredInfo, optionalInfo)
            activateMemberLogic(info, meta, activateMemberCommand, replyTo)
          }
          case RegisteredMemberState(info, meta) => activateMemberLogic(info, meta, activateMemberCommand, replyTo)
          case _: TerminatedMemberState => useTerminatedStateErrorStatusReply(replyTo)
        }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "ActivateMember")
    }
  }

  private def suspendMemberLogic(
    info: MemberInfo,
    meta: MemberMetaInfo,
    suspendMemberCommand: SuspendMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateMemberInfo(info) match {
      case Valid(_) =>
        val newMeta = meta.copy(
          lastModifiedBy = suspendMemberCommand.suspendingMember,
          lastModifiedOn = Some(Timestamp(Instant.now(clock))),
          currentState = MEMBER_STATUS_SUSPENDED
        )
        val event = MemberSuspended(suspendMemberCommand.memberId, Some(newMeta))
        Effect
          .persist(event)
          .thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "SuspendMember")
    }
  }

  private def suspendMember(
    state: MemberState,
    suspendMemberCommand: SuspendMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateSuspendMemberCommand(suspendMemberCommand) match {
      case Valid(_) =>
        state match {
          case _: DraftMemberState => useErrorStatusReply(replyTo, s"Member has not yet been registered.")
          case RegisteredMemberState(info, meta) => suspendMemberLogic(info, meta, suspendMemberCommand, replyTo)
          case _: TerminatedMemberState => useTerminatedStateErrorStatusReply(replyTo)
        }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "SuspendMember")
    }
  }

  private def terminateMemberLogic(
    meta: MemberMetaInfo,
    terminateMemberCommand: TerminateMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    val newMeta = meta.copy(
      lastModifiedBy = terminateMemberCommand.terminatingMember,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )
    val event = MemberTerminated(terminateMemberCommand.memberId, Some(newMeta))
    Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }
  }

  private def terminateMember(
    state: MemberState,
    terminateMemberCommand: TerminateMember,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateTerminateMemberCommand(terminateMemberCommand) match {
      case Valid(_) =>
        state match {
          case _: DraftMemberState => useErrorStatusReply(replyTo, s"Member has not yet been registered.")
          case RegisteredMemberState(_, meta) => terminateMemberLogic(meta, terminateMemberCommand, replyTo)
          case _: TerminatedMemberState => useTerminatedStateErrorStatusReply(replyTo)
        }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "TerminateMember")
    }
  }

  private def editMemberInfoLogic(
    info: MemberInfo,
    metaInfo: MemberMetaInfo,
    editMemberInfoCommand: EditMemberInfo,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    if (metaInfo.createdBy.isEmpty) {
      useErrorStatusReply(replyTo, s"A member not registered cannot be edited")
    } else if (metaInfo.currentState.isMemberStatusSuspended) {
      useErrorStatusReply(replyTo, s"Cannot edit info for suspended members")
    } else {
      val editInfo = editMemberInfoCommand.memberInfo.get
      val newInfo = info.copy(
        handle = editInfo.handle.getOrElse(info.handle),
        avatarUrl = editInfo.avatarUrl.getOrElse(info.avatarUrl),
        firstName = editInfo.firstName.getOrElse(info.firstName),
        lastName = editInfo.lastName.getOrElse(info.lastName),
        notificationPreference = editInfo.notificationPreference.orElse(info.notificationPreference),
        notificationOptIn = editInfo.notificationPreference.fold(info.notificationOptIn)(_ => true),
        contact = editInfo.contact.orElse(info.contact),
        organizationMembership = if (editInfo.organizationMembership.nonEmpty) editInfo.organizationMembership else info.organizationMembership,
        tenant = editInfo.tenant.orElse(info.tenant)
      )

      val newMeta = metaInfo.copy(
        lastModifiedBy = editMemberInfoCommand.editingMember,
        lastModifiedOn = Some(Timestamp(Instant.now(clock)))
      )
      val event = MemberInfoEdited(editMemberInfoCommand.memberId, Some(newInfo), Some(newMeta))
      Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }
    }
  }

  private def editMemberInfo(
    state: MemberState,
    editMemberInfoCommand: EditMemberInfo,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateEditMemberInfo(editMemberInfoCommand) match {
      case Valid(_) =>
        state match {
          case DraftMemberState(requiredInfo, optionalInfo, meta) =>
            val info = createMemberInfoFromDraftState(requiredInfo, optionalInfo)
            editMemberInfoLogic(info, meta, editMemberInfoCommand, replyTo)
          case RegisteredMemberState(info, meta) => editMemberInfoLogic(info, meta, editMemberInfoCommand, replyTo)
          case _: TerminatedMemberState => useTerminatedStateErrorStatusReply(replyTo)
        }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "EditMemberInfo")
    }
  }

  private def getMemberInfoLogic (
    info: MemberInfo,
    metaInfo: MemberMetaInfo,
    getMemberInfoCommand: GetMemberInfo,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    val (infoOpt, metaOpt): (Option[MemberInfo], Option[MemberMetaInfo]) =
      metaInfo.createdBy.fold[(Option[MemberInfo], Option[MemberMetaInfo])]((None, None)) {
        _ => (Some(info), Some(metaInfo))
      }
    Effect.reply(replyTo) {
      StatusReply.Success(
        MemberData(getMemberInfoCommand.memberId, infoOpt, metaOpt)
      )
    }
  }

  private def getMemberInfo(
    state: MemberState,
    getMemberInfoCommand: GetMemberInfo,
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    MemberValidation.validateGetMemberInfo(getMemberInfoCommand) match {
      case Valid(_) =>
        state match {
          case DraftMemberState(requiredInfo, optionalInfo, meta) =>
            val info = createMemberInfoFromDraftState(requiredInfo, optionalInfo)
            getMemberInfoLogic(info, meta, getMemberInfoCommand, replyTo)
          case RegisteredMemberState(info, meta) => getMemberInfoLogic(info, meta, getMemberInfoCommand, replyTo)
          case _: TerminatedMemberState => useTerminatedStateErrorStatusReply(replyTo)
        }
      case Invalid(errors) =>
        useCommandValidationErrorStatusReply(replyTo, errors, "GetMemberInfo")
    }
  }

  // Helpers

  private def useCommandValidationErrorStatusReply(
    replyTo: ActorRef[StatusReply[MemberResponse]],
    errors: data.NonEmptyChain[MemberValidation.MemberValidationError],
    commandName: String
  ): ReplyEffect[MemberEvent, MemberState] = {
    val message = s"Validation failed for $commandName with errors: ${
      errors.map {
        _.errorMessage
      }.toList.mkString(", ")
    }"
    useErrorStatusReply(replyTo, message)
  }

  private def useTerminatedStateErrorStatusReply(
    replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    useErrorStatusReply(replyTo, s"Terminated members cannot process messages")
  }

  private def useErrorStatusReply(
    replyTo: ActorRef[StatusReply[MemberResponse]],
    string: String
  ): ReplyEffect[MemberEvent, MemberState] = {
    Effect.reply(replyTo) {
      StatusReply.Error(string)
    }
  }

  private def createMemberInfoFromDraftState(
    requiredInfo: RequiredDraftInfo,
    optionalInfo: OptionalDraftInfo
  ): MemberInfo = {
    MemberInfo(
      handle = requiredInfo.handle,
      avatarUrl = requiredInfo.avatarUrl,
      firstName = requiredInfo.firstName,
      lastName = requiredInfo.lastName,
      notificationPreference = optionalInfo.notificationPreference,
      notificationOptIn = optionalInfo.notificationPreference.isDefined,
      contact = requiredInfo.contact,
      organizationMembership = optionalInfo.organizationMembership,
      tenant = requiredInfo.tenant
    )
  }
}
