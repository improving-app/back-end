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
import com.improving.app.member.domain.{MemberState => StateEnum}
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}

object Member extends StrictLogging {

  private val clock: Clock = Clock.systemDefaultZone()

  val MemberEntityKey: EntityTypeKey[MemberCommand] = EntityTypeKey[MemberCommand]("Member")

  //Command wraps the request type
  final case class MemberCommand(request: MemberRequest, replyTo: ActorRef[StatusReply[MemberResponse]])

  private def emptyState(): MemberState = {
    UninitializedMemberState()
  }

  case class StateError(str: String)

  sealed trait MemberState

  case class UninitializedMemberState() extends MemberState

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
      val result: Either[StateError, MemberResponse] = state match {
        case UninitializedMemberState() =>
          command.request match {
            case registerMemberCommand: RegisterMember => registerMember(registerMemberCommand)
            case getMemberInfoCommand: GetMemberInfo => Right(MemberData(getMemberInfoCommand.memberId, None, None))
            case _ => Left(StateError(s"${command.request.productPrefix} command cannot be used on an uninitialized Member"))
          }
        case DraftMemberState(requiredInfo, optionalInfo, meta) =>
          command.request match {
            case activateMemberCommand: ActivateMember =>
              val info = createMemberInfoFromDraftState(requiredInfo, optionalInfo)
              activateMember(info, meta, activateMemberCommand)
            case editMemberInfoCommand: EditMemberInfo =>
              val info = createMemberInfoFromDraftState(requiredInfo, optionalInfo)
              editMemberInfo(info, meta, editMemberInfoCommand)
            case getMemberInfoCommand: GetMemberInfo =>
              val info = createMemberInfoFromDraftState(requiredInfo, optionalInfo)
              getMemberInfo(info, meta, getMemberInfoCommand)
            case _ => Left(StateError(s"${command.request.productPrefix} command cannot be used on a draft Member"))
          }
        case RegisteredMemberState(info, meta) =>
          meta.currentState match {
            case MemberState.MEMBER_STATUS_ACTIVE =>
              command.request match {
                case suspendMemberCommand: SuspendMember => suspendMember(info, meta, suspendMemberCommand)
                case terminateMemberCommand: TerminateMember => terminateMember(meta, terminateMemberCommand)
                case editMemberInfoCommand: EditMemberInfo => editMemberInfo(info, meta, editMemberInfoCommand)
                case getMemberInfoCommand: GetMemberInfo => getMemberInfo(info, meta, getMemberInfoCommand)
                case _ => Left(StateError(s"${command.request.productPrefix} command cannot be used on an active Member"))
              }
            case MemberState.MEMBER_STATUS_SUSPENDED =>
              command.request match {
                case activateMemberCommand: ActivateMember => activateMember(info, meta, activateMemberCommand)
                case suspendMemberCommand: SuspendMember => suspendMember(info, meta, suspendMemberCommand)
                case terminateMemberCommand: TerminateMember => terminateMember(meta, terminateMemberCommand)
                case getMemberInfoCommand: GetMemberInfo => getMemberInfo(info, meta, getMemberInfoCommand)
                case _ => Left(StateError(s"${command.request.productPrefix} command cannot be used on a suspended Member"))
              }
            case _ => Left(StateError(s"Registered member has an invalid state ${meta.currentState.productPrefix}"))
          }
        case _: TerminatedMemberState =>
          command.request match {
            case _ => Left(StateError(s"${command.request.productPrefix} command cannot be used on a terminated Member"))
          }
      }

      result match {
        case Left(error) => Effect.reply(command.replyTo)(StatusReply.Error(error.str))
        case Right(event) =>
          event match {
            case _: MemberData =>
              Effect.reply(command.replyTo) { StatusReply.Success(event) }
            case x: MemberEventResponse =>
              Effect.persist(x.memberEvent)
                .thenReply(command.replyTo) { a: MemberState => StatusReply.Success(event) }
            case _ => Effect.reply(command.replyTo)(StatusReply.Error(s"${event.productPrefix} is not a supported member response"))
          }
      }
  }

  //EventHandler
  private val eventHandler: (MemberState, MemberEvent) => MemberState = { (state, event) =>
    event match {
      case memberRegisteredEvent: MemberRegistered =>
        state match {
          case _: UninitializedMemberState => DraftMemberState(
            requiredInfo = RequiredDraftInfo(
              contact = memberRegisteredEvent.memberInfo.get.contact,
              handle = memberRegisteredEvent.memberInfo.get.handle,
              avatarUrl = memberRegisteredEvent.memberInfo.get.avatarUrl,
              firstName = memberRegisteredEvent.memberInfo.get.firstName,
              lastName = memberRegisteredEvent.memberInfo.get.lastName,
              tenant = memberRegisteredEvent.memberInfo.get.tenant
            ),
            optionalInfo = OptionalDraftInfo(
              notificationPreference = memberRegisteredEvent.memberInfo.get.notificationPreference,
              organizationMembership = memberRegisteredEvent.memberInfo.get.organizationMembership
            ),
            meta = memberRegisteredEvent.meta.get
          )
          case _ => state
        }

      case memberActivatedEvent: MemberActivated =>
        state match {
          case DraftMemberState(requiredInfo, optionalInfo, _) => RegisteredMemberState(
            info = createMemberInfoFromDraftState(requiredInfo, optionalInfo),
            meta = memberActivatedEvent.meta.get
          )
          case x: RegisteredMemberState =>
            x.copy(meta = memberActivatedEvent.meta.get)
          case _ => state
        }

      case memberSuspendedEvent: MemberSuspended =>
        state match {
          case x: RegisteredMemberState => x.copy(meta = memberSuspendedEvent.meta.get)
          case _ => state
        }

      case memberTerminatedEvent: MemberTerminated =>
        state match {
          case _: RegisteredMemberState => TerminatedMemberState(lastMeta = memberTerminatedEvent.lastMeta.get)
          case _ => state
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
          case _ => state
        }

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled event $other")
    }
  }

  private def registerMember(
    registerMemberCommand: RegisterMember
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateRegisterMember(registerMemberCommand) match {
      case Valid(_) =>
        val now = Some(Timestamp(Instant.now(clock)))
        val newMeta = MemberMetaInfo(
          lastModifiedOn = now,
          lastModifiedBy = registerMemberCommand.registeringMember,
          createdOn = now,
          createdBy = registerMemberCommand.registeringMember,
          currentState = StateEnum.MEMBER_STATUS_DRAFT
        )
        val event = MemberRegistered(
          registerMemberCommand.memberId,
          registerMemberCommand.memberInfo,
          Some(newMeta)
        )
        Right(MemberEventResponse(event))
      case Invalid(errors) =>
        Left(StateError(useCommandValidationError(errors, s"${registerMemberCommand.productPrefix}")))
    }
  }

  private def activateMember(
    info: MemberInfo,
    meta: MemberMetaInfo,
    activateMemberCommand: ActivateMember
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateActivateMemberCommand(activateMemberCommand) match {
      case Valid(_) =>
        val newMeta = meta.copy(
          lastModifiedBy = activateMemberCommand.activatingMember,
          lastModifiedOn = Some(Timestamp(Instant.now(clock))),
          currentState = StateEnum.MEMBER_STATUS_ACTIVE
        )
        transitionMemberState(
          info = info,
          issuingCommand = activateMemberCommand.productPrefix,
          createEvent = () => {
            MemberEventResponse(
              MemberActivated(
                activateMemberCommand.memberId,
                Some(newMeta)
              )
            )
          }
        )
      case Invalid(errors) =>
        Left(StateError(useCommandValidationError(errors, s"${activateMemberCommand.productPrefix}")))
    }
  }

  private def suspendMember(
    info: MemberInfo,
    meta: MemberMetaInfo,
    suspendMemberCommand: SuspendMember,
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateSuspendMemberCommand(suspendMemberCommand) match {
      case Valid(_) =>
        val newMeta = meta.copy(
          lastModifiedBy = suspendMemberCommand.suspendingMember,
          lastModifiedOn = Some(Timestamp(Instant.now(clock))),
          currentState = StateEnum.MEMBER_STATUS_SUSPENDED
        )
        transitionMemberState(
          info = info,
          issuingCommand = suspendMemberCommand.productPrefix,
          createEvent = () => {
            MemberEventResponse(
              MemberSuspended(
                suspendMemberCommand.memberId,
                Some(newMeta)
              )
            )
          }
        )
      case Invalid(errors) =>
        Left(StateError(useCommandValidationError(errors, s"${suspendMemberCommand.productPrefix}")))
    }
  }

  private def terminateMember(
    meta: MemberMetaInfo,
    terminateMemberCommand: TerminateMember
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateTerminateMemberCommand(terminateMemberCommand) match {
      case Valid(_) =>
        val newMeta = meta.copy(
          lastModifiedBy = terminateMemberCommand.terminatingMember,
          lastModifiedOn = Some(Timestamp(Instant.now(clock)))
        )
        val event = MemberTerminated(terminateMemberCommand.memberId, Some(newMeta))
        Right(MemberEventResponse(event))
      case Invalid(errors) =>
        Left(StateError(useCommandValidationError(errors, s"${terminateMemberCommand.productPrefix}")))
    }
  }

  private def editMemberInfo(
    info: MemberInfo,
    meta: MemberMetaInfo,
    editMemberInfoCommand: EditMemberInfo
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateEditMemberInfo(editMemberInfoCommand) match {
      case Valid(_) =>
        val newInfo = updateInfoFromEditInfo(info, editMemberInfoCommand.memberInfo.get)

        val newMeta = meta.copy(
          lastModifiedBy = editMemberInfoCommand.editingMember,
          lastModifiedOn = Some(Timestamp(Instant.now(clock)))
        )
        val event = MemberInfoEdited(editMemberInfoCommand.memberId, Some(newInfo), Some(newMeta))
        Right(MemberEventResponse(event))
      case Invalid(errors) =>
        Left(StateError(useCommandValidationError(errors, s"${editMemberInfoCommand.productPrefix}")))
    }
  }

  private def getMemberInfo(
    info: MemberInfo,
    meta: MemberMetaInfo,
    getMemberInfoCommand: GetMemberInfo,
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateGetMemberInfo(getMemberInfoCommand) match {
      case Valid(_) =>
        Right(MemberData(getMemberInfoCommand.memberId, Some(info), Some(meta)))
      case Invalid(errors) =>
        Left(StateError(useCommandValidationError(errors, s"${getMemberInfoCommand.productPrefix}")))
    }
  }

  // Helpers

  private def updateInfoFromEditInfo(info: MemberInfo, editableInfo: EditableInfo): MemberInfo = {
    info.copy(
      handle = editableInfo.handle.getOrElse(info.handle),
      avatarUrl = editableInfo.avatarUrl.getOrElse(info.avatarUrl),
      firstName = editableInfo.firstName.getOrElse(info.firstName),
      lastName = editableInfo.lastName.getOrElse(info.lastName),
      notificationPreference = editableInfo.notificationPreference.orElse(info.notificationPreference),
      notificationOptIn = editableInfo.notificationPreference.fold(info.notificationOptIn)(_ => true),
      contact = editableInfo.contact.orElse(info.contact),
      organizationMembership = if (editableInfo.organizationMembership.nonEmpty) editableInfo.organizationMembership else info.organizationMembership,
      tenant = editableInfo.tenant.orElse(info.tenant)
    )
  }

  private def transitionMemberState(
    info: MemberInfo,
    issuingCommand: String,
    createEvent: () => MemberResponse
  ): Either[StateError, MemberResponse] = {
    MemberValidation.validateMemberInfo(info) match {
      case Valid(_) =>
        Right(createEvent())
      case Invalid(errors) =>
        val message = useStateInfoInsufficientError(errors, issuingCommand)
        Left(StateError(message))
    }
  }

  private def useStateInfoInsufficientError(errors: data.NonEmptyChain[MemberValidation.MemberValidationError], commandName: String): String = {
    s"member info is not sufficiently filled out to complete the ${commandName} command: ${
      errors.map {
        _.errorMessage
      }.toList.mkString(", ")
    }"
  }

  private def useCommandValidationError(
    errors: data.NonEmptyChain[MemberValidation.MemberValidationError],
    commandName: String
  ): String = {
    s"Validation failed for $commandName with errors: ${
      errors.map {
        _.errorMessage
      }.toList.mkString(", ")
    }"
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
