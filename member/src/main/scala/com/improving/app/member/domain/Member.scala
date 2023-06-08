package com.improving.app.member.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.MemberId
import com.improving.app.common.errors.{Error, StateError}
import com.improving.app.member.domain.MemberState.{MEMBER_STATE_ACTIVE, MEMBER_STATE_DRAFT, MEMBER_STATE_SUSPENDED}
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

  sealed trait MemberState

  case class UninitializedMemberState() extends MemberState

  case class DraftMemberState(editableInfo: EditableInfo, meta: MemberMetaInfo) extends MemberState
  case class RegisteredMemberState(info: MemberInfo, meta: MemberMetaInfo) extends MemberState
  case class TerminatedMemberState(lastMeta: MemberMetaInfo) extends MemberState

  trait HasMemberId {
    def memberId: Option[MemberId]
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

  //CommandHandler
  private val commandHandler: (MemberState, MemberCommand) => ReplyEffect[MemberEvent, MemberState] = {
    (state, command) =>
      val result: Either[Error, MemberResponse] = state match {
        case UninitializedMemberState() =>
          command.request match {
            case registerMemberCommand: RegisterMember => registerMember(registerMemberCommand)
            case _ =>
              Left(StateError(s"${command.request.productPrefix} command cannot be used on an uninitialized Member"))
          }
        case DraftMemberState(editableInfo, meta) =>
          command.request match {
            case activateMemberCommand: ActivateMember =>
              val info = createMemberInfoFromDraftState(editableInfo)
              activateMember(info, meta, activateMemberCommand)
            case editMemberInfoCommand: EditMemberInfo =>
              val info = createMemberInfoFromDraftState(editableInfo)
              editMemberInfo(info, meta, editMemberInfoCommand)
            case getMemberInfoCommand: GetMemberInfo =>
              val info = createMemberInfoFromDraftState(editableInfo)
              getMemberInfo(info, meta, getMemberInfoCommand)
            case _ => Left(StateError(s"${command.request.productPrefix} command cannot be used on a draft Member"))
          }
        case RegisteredMemberState(info, meta) =>
          meta.currentState match {
            case MemberState.MEMBER_STATE_ACTIVE =>
              command.request match {
                case suspendMemberCommand: SuspendMember     => suspendMember(info, meta, suspendMemberCommand)
                case terminateMemberCommand: TerminateMember => terminateMember(meta, terminateMemberCommand)
                case editMemberInfoCommand: EditMemberInfo   => editMemberInfo(info, meta, editMemberInfoCommand)
                case getMemberInfoCommand: GetMemberInfo     => getMemberInfo(info, meta, getMemberInfoCommand)
                case _ =>
                  Left(StateError(s"${command.request.productPrefix} command cannot be used on an active Member"))
              }
            case MemberState.MEMBER_STATE_SUSPENDED =>
              command.request match {
                case activateMemberCommand: ActivateMember   => activateMember(info, meta, activateMemberCommand)
                case suspendMemberCommand: SuspendMember     => suspendMember(info, meta, suspendMemberCommand)
                case terminateMemberCommand: TerminateMember => terminateMember(meta, terminateMemberCommand)
                case getMemberInfoCommand: GetMemberInfo     => getMemberInfo(info, meta, getMemberInfoCommand)
                case _ =>
                  Left(StateError(s"${command.request.productPrefix} command cannot be used on a suspended Member"))
              }
            case _ => Left(StateError(s"Registered member has an invalid state ${meta.currentState.productPrefix}"))
          }
        case _: TerminatedMemberState =>
          command.request match {
            case _ =>
              Left(StateError(s"${command.request.productPrefix} command cannot be used on a terminated Member"))
          }
      }

      result match {
        case Left(error) => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
        case Right(event) =>
          event match {
            case _: MemberData =>
              Effect.reply(command.replyTo) { StatusReply.Success(event) }
            case x: MemberEventResponse =>
              Effect
                .persist(x.memberEvent)
                .thenReply(command.replyTo) { a: MemberState => StatusReply.Success(event) }
            case _ =>
              Effect.reply(command.replyTo)(
                StatusReply.Error(s"${event.productPrefix} is not a supported member response")
              )
          }
      }
  }

  //EventHandler
  private val eventHandler: (MemberState, MemberEvent) => MemberState = { (state, event) =>
    event match {
      case memberRegisteredEvent: MemberRegistered =>
        state match {
          case _: UninitializedMemberState =>
            DraftMemberState(
              editableInfo = editableInfoFromMemberInfo(memberRegisteredEvent.getMemberInfo),
              meta = memberRegisteredEvent.getMeta
            )
          case _ => state
        }

      case memberActivatedEvent: MemberActivated =>
        state match {
          case DraftMemberState(editableInfo, _) =>
            RegisteredMemberState(
              info = createMemberInfoFromDraftState(editableInfo),
              meta = memberActivatedEvent.getMeta
            )
          case x: RegisteredMemberState =>
            x.copy(meta = memberActivatedEvent.getMeta)
          case _ => state
        }

      case memberSuspendedEvent: MemberSuspended =>
        state match {
          case x: RegisteredMemberState => x.copy(meta = memberSuspendedEvent.getMeta)
          case _                        => state
        }

      case memberTerminatedEvent: MemberTerminated =>
        state match {
          case _: RegisteredMemberState => TerminatedMemberState(lastMeta = memberTerminatedEvent.getLastMeta)
          case _                        => state
        }

      case memberInfoEdited: MemberInfoEdited =>
        state match {
          case _: DraftMemberState =>
            val info = memberInfoEdited.getMemberInfo
            DraftMemberState(
              editableInfo = editableInfoFromMemberInfo(info),
              meta = memberInfoEdited.getMeta
            )
          case _: RegisteredMemberState =>
            RegisteredMemberState(
              info = memberInfoEdited.getMemberInfo,
              meta = memberInfoEdited.getMeta
            )
          case _ => state
        }

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled event $other")
    }
  }

  private def registerMember(
      registerMemberCommand: RegisterMember
  ): Either[Error, MemberResponse] = {
    logger.info("registering")
    val now = Timestamp(Instant.now(clock))
    val newMeta = MemberMetaInfo(
      lastModifiedOn = Some(now),
      lastModifiedBy = registerMemberCommand.registeringMember,
      createdOn = Some(now),
      createdBy = registerMemberCommand.registeringMember,
      currentState = MEMBER_STATE_DRAFT
    )
    val event = MemberRegistered(
      registerMemberCommand.memberId,
      registerMemberCommand.memberInfo,
      Some(newMeta)
    )
    Right(MemberEventResponse(event))
  }

  private def activateMember(
      info: MemberInfo,
      meta: MemberMetaInfo,
      activateMemberCommand: ActivateMember
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = activateMemberCommand.activatingMember,
      lastModifiedOn = Some(Timestamp(Instant.now(clock))),
      currentState = MEMBER_STATE_ACTIVE
    )
    transitionMemberState(
      info = info,
      issuingCommand = activateMemberCommand.productPrefix,
      event = MemberEventResponse(
        MemberActivated(
          activateMemberCommand.memberId,
          Some(newMeta)
        )
      )
    )
  }

  private def suspendMember(
      info: MemberInfo,
      meta: MemberMetaInfo,
      suspendMemberCommand: SuspendMember,
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = suspendMemberCommand.suspendingMember,
      lastModifiedOn = Some(Timestamp(Instant.now(clock))),
      currentState = MEMBER_STATE_SUSPENDED
    )
    transitionMemberState(
      info = info,
      issuingCommand = suspendMemberCommand.productPrefix,
      event = MemberEventResponse(
        MemberSuspended(
          suspendMemberCommand.memberId,
          Some(newMeta)
        )
      )
    )
  }

  private def terminateMember(
      meta: MemberMetaInfo,
      terminateMemberCommand: TerminateMember
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = terminateMemberCommand.terminatingMember,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )
    val event = MemberTerminated(terminateMemberCommand.memberId, Some(newMeta))
    Right(MemberEventResponse(event))
  }

  private def editMemberInfo(
      info: MemberInfo,
      meta: MemberMetaInfo,
      editMemberInfoCommand: EditMemberInfo
  ): Either[Error, MemberResponse] = {
    val newInfo = editMemberInfoCommand.memberInfo.map(editable => updateInfoFromEditInfo(info, editable))

    val newMeta = meta.copy(
      lastModifiedBy = editMemberInfoCommand.editingMember,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )
    val event = MemberInfoEdited(editMemberInfoCommand.memberId, newInfo, Some(newMeta))
    Right(MemberEventResponse(event))
  }

  private def getMemberInfo(
      info: MemberInfo,
      meta: MemberMetaInfo,
      getMemberInfoCommand: GetMemberInfo,
  ): Either[Error, MemberResponse] = Right(MemberData(getMemberInfoCommand.memberId, Some(info), Some(meta)))

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
      organizationMembership =
        if (editableInfo.organizationMembership.nonEmpty) editableInfo.organizationMembership
        else info.organizationMembership,
      tenant = editableInfo.tenant.orElse(info.tenant)
    )
  }

  private def transitionMemberState(
      info: MemberInfo,
      issuingCommand: String,
      event: MemberResponse
  ): Either[Error, MemberResponse] = Right(event)

  private def createMemberInfoFromDraftState(
      editableInfo: EditableInfo
  ): MemberInfo = MemberInfo(
    handle = editableInfo.getHandle,
    avatarUrl = editableInfo.getAvatarUrl,
    firstName = editableInfo.getFirstName,
    lastName = editableInfo.getLastName,
    notificationPreference = editableInfo.notificationPreference,
    notificationOptIn = editableInfo.notificationPreference.isDefined,
    contact = editableInfo.contact,
    organizationMembership = editableInfo.organizationMembership,
    tenant = editableInfo.tenant
  )

  private def editableInfoFromMemberInfo(memberInfo: MemberInfo): EditableInfo = EditableInfo(
    contact = memberInfo.contact,
    handle = Some(memberInfo.handle),
    avatarUrl = Some(memberInfo.avatarUrl),
    firstName = Some(memberInfo.firstName),
    lastName = Some(memberInfo.lastName),
    tenant = memberInfo.tenant,
    notificationPreference = memberInfo.notificationPreference,
    organizationMembership = memberInfo.organizationMembership
  )
}
