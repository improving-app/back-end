package com.improving.app.member.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.OpenTelemetry
import com.improving.app.common.domain.MemberId
import com.improving.app.common.errors.{Error, StateError}
import com.improving.app.member.domain.MemberState.{MEMBER_STATE_ACTIVE, MEMBER_STATE_DRAFT, MEMBER_STATE_SUSPENDED}
import com.improving.app.member.domain.Validation.{draftTransitionMemberInfoValidator, memberCommandValidator, memberQueryValidator}
import com.improving.app.member.domain.util.{EditableMemberInfoUtil, MemberInfoUtil}
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}

object Member extends StrictLogging {

  private val clock: Clock = Clock.systemDefaultZone()

  val MemberEntityKey: EntityTypeKey[MemberEnvelope] = EntityTypeKey[MemberEnvelope]("Member")

  // Command wraps the request type
  final case class MemberEnvelope(request: MemberRequestPB, replyTo: ActorRef[StatusReply[MemberResponse]])

  private def emptyState(): MemberState = {
    UninitializedMemberState
  }

  sealed private[domain] trait MemberState
  private[domain] object UninitializedMemberState extends MemberState
  sealed private[domain] trait RegisteredMemberState extends MemberState {
    val meta: MemberMetaInfo
  }

  sealed private[domain] trait DefinedMemberState extends MemberState {
    val info: MemberInfo
    val meta: MemberMetaInfo
  }

  private[domain] case class DraftMemberState(editableInfo: EditableInfo, meta: MemberMetaInfo)
      extends RegisteredMemberState
  private[domain] case class ActivatedMemberState(info: MemberInfo, meta: MemberMetaInfo)
      extends RegisteredMemberState
      with DefinedMemberState

  private[domain] case class SuspendedMemberState(info: MemberInfo, meta: MemberMetaInfo, suspensionReason: String)
      extends RegisteredMemberState
      with DefinedMemberState
  private[domain] case class TerminatedMemberState(lastMeta: MemberMetaInfo) extends MemberState

  trait HasMemberId {
    def memberId: Option[MemberId]
  }

  def apply(entityTypeHint: String, memberId: String): Behavior[MemberEnvelope] =
    Behaviors.setup { context =>
      context.log.info("Starting Member {}", memberId)
      EventSourcedBehavior
        .withEnforcedReplies[MemberEnvelope, MemberResponse, MemberState](
          persistenceId = PersistenceId(entityTypeHint, memberId),
          emptyState = emptyState(),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        // .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Member {} stopped", memberId)
        }
    }

  // CommandHandler
  private val commandHandler: (MemberState, MemberEnvelope) => ReplyEffect[MemberResponse, MemberState] = {
    (state, command) =>
      def replyWithResponseEvent(response: MemberResponse): ReplyEffect[MemberResponse, MemberState] = response match {
        case eventResponse: MemberEventResponse =>
          Effect
            .persist(eventResponse)
            .thenReply(command.replyTo) { _: MemberState => StatusReply.Success(response) }
        case _: MemberData =>
          Effect
            .reply(command.replyTo)(StatusReply.Success(response))
        case _ =>
          Effect.reply(command.replyTo)(
            StatusReply.Error(s"${response.productPrefix} is not a supported member response")
          )
      }
      command.request match {
        case c: MemberCommand =>
          memberCommandValidator(c) match {
            case None =>
              val result: Either[Error, MemberResponse] = state match {
                case UninitializedMemberState =>
                  command.request match {
                    case registerMemberCommand: RegisterMember => registerMember(registerMemberCommand)
                    case _ =>
                      Left(
                        StateError(
                          s"${command.request.productPrefix} command cannot be used on an uninitialized Member"
                        )
                      )
                  }
                case DraftMemberState(editableInfo, meta) =>
                  command.request match {
                    case activateMemberCommand: ActivateMember =>
                      activateMember(Left(editableInfo), meta, activateMemberCommand)
                    case editMemberInfoCommand: EditMemberInfo =>
                      editMemberInfo(Left(editableInfo), meta, editMemberInfoCommand)
                    case _ =>
                      Left(StateError(s"${command.request.productPrefix} command cannot be used on a draft Member"))
                  }
                case x: DefinedMemberState =>
                  x.meta.currentState match {
                    case MemberState.MEMBER_STATE_ACTIVE =>
                      command.request match {
                        case suspendMemberCommand: SuspendMember     => suspendMember(x.meta, suspendMemberCommand)
                        case terminateMemberCommand: TerminateMember => terminateMember(x.meta, terminateMemberCommand)
                        case editMemberInfoCommand: EditMemberInfo =>
                          editMemberInfo(Right(x.info), x.meta, editMemberInfoCommand)
                        case _ =>
                          Left(
                            StateError(s"${command.request.productPrefix} command cannot be used on an active Member")
                          )
                      }
                    case MemberState.MEMBER_STATE_SUSPENDED =>
                      command.request match {
                        case activateMemberCommand: ActivateMember =>
                          activateMember(Right(x.info), x.meta, activateMemberCommand)
                        case suspendMemberCommand: SuspendMember     => suspendMember(x.meta, suspendMemberCommand)
                        case terminateMemberCommand: TerminateMember => terminateMember(x.meta, terminateMemberCommand)
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} command cannot be used on a suspended Member"
                            )
                          )
                      }
                    case _ =>
                      Left(StateError(s"Registered member has an invalid state ${x.meta.currentState.productPrefix}"))
                  }
                case _: TerminatedMemberState =>
                  command.request match {
                    case _ =>
                      Left(
                        StateError(s"${command.request.productPrefix} command cannot be used on a terminated Member")
                      )
                  }
              }

              result match {
                case Left(error)  => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
                case Right(event) => replyWithResponseEvent(event)
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.message))
          }
        case q: MemberQuery =>
          memberQueryValidator(q) match {
            case None =>
              q match {
                case request: GetMemberInfo =>
                  state match {
                    case x: DefinedMemberState =>
                      getMemberInfo(Right(x.info), x.meta, request) match {
                        case Left(error)  => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
                        case Right(event) => replyWithResponseEvent(event)
                      }
                    case x: DraftMemberState =>
                      getMemberInfo(Left(x.editableInfo), x.meta, request) match {
                        case Left(error)  => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
                        case Right(event) => replyWithResponseEvent(event)

                      }
                    case UninitializedMemberState =>
                      Effect.reply(command.replyTo)(
                        StatusReply.Error(
                          s"${command.request.productPrefix} command cannot be used on an uninitialized Member"
                        )
                      )
                    case _: TerminatedMemberState =>
                      Effect.reply(command.replyTo)(
                        StatusReply.Error(
                          s"${command.request.productPrefix} command cannot be used on a terminated Member"
                        )
                      )
                  }
                case _ => Effect.reply(command.replyTo)(StatusReply.Error("Message was not a MemberRequest"))
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.toString))
          }
        case MemberRequestPB.Empty =>
          Effect.reply(command.replyTo)(StatusReply.Error("Message was not a MemberRequest"))
      }
  }

  // EventHandler
  private val eventHandler: (MemberState, MemberResponse) => MemberState = { (state, response) =>
    response match {
      case eventResponse: MemberEventResponse =>
        eventResponse.memberEvent match {
          case memberRegisteredEvent: MemberRegistered =>
            state match {
              case UninitializedMemberState =>
                DraftMemberState(
                  editableInfo = memberRegisteredEvent.getMemberInfo,
                  meta = memberRegisteredEvent.getMeta
                )
              case _ => state
            }

          case memberActivatedEvent: MemberActivated =>
            state match {
              case DraftMemberState(editableInfo, _) =>
                ActivatedMemberState(
                  info = editableInfo.toInfo,
                  meta = memberActivatedEvent.getMeta
                )
              case x: SuspendedMemberState =>
                ActivatedMemberState(info = x.info, meta = memberActivatedEvent.getMeta)
              case _ => state
            }

          case memberSuspendedEvent: MemberSuspended =>
            state match {
              case x: DefinedMemberState =>
                SuspendedMemberState(
                  info = x.info,
                  meta = memberSuspendedEvent.getMeta,
                  suspensionReason = memberSuspendedEvent.suspensionReason
                )
              case _ => state
            }

          case memberTerminatedEvent: MemberTerminated =>
            state match {
              case _: RegisteredMemberState => TerminatedMemberState(lastMeta = memberTerminatedEvent.getLastMeta)
              case _                        => state
            }

          case memberInfoEdited: MemberInfoEdited =>
            state match {
              case _: DraftMemberState =>
                val info = memberInfoEdited.getNewInfo
                DraftMemberState(
                  editableInfo = info,
                  meta = memberInfoEdited.getMeta
                )
              case _: ActivatedMemberState =>
                ActivatedMemberState(
                  info = memberInfoEdited.getNewInfo.toInfo,
                  meta = memberInfoEdited.getMeta
                )
              case _ => state
            }
          case _ => state
        }
      case _: MemberData        => state
      case MemberResponse.Empty => state

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled event $other")
    }
  }

  // Open Telemetry Metrics
  private val registeredMembers: OpenTelemetry.Counter = {
    val counter = OpenTelemetry.Counter("registered-members","members",
      "Tracks total number of registered members","each")
    counter.add(0L) // FIXME: initialize to # of registered members in DB
    counter
  }
  private val activeMembers: OpenTelemetry.Counter = {
    val counter = OpenTelemetry.Counter("active-members", "members",
      "Tracks total number of active members", "each")
    counter.add(0L) // FIXME: initialize to # of active members in DB
    counter
  }

  private def registerMember(
      registerMemberCommand: RegisterMember
  ): Either[Error, MemberResponse] = {
    logger.info("registering")
    val now = Timestamp(Instant.now(clock))
    val newMeta = MemberMetaInfo(
      lastModifiedOn = Some(now),
      lastModifiedBy = registerMemberCommand.onBehalfOf,
      createdOn = Some(now),
      createdBy = registerMemberCommand.onBehalfOf,
      currentState = MEMBER_STATE_DRAFT
    )
    val event = MemberRegistered(
      registerMemberCommand.memberId,
      registerMemberCommand.memberInfo,
      Some(newMeta)
    )
    registeredMembers.add(1L)
    Right(MemberEventResponse(event))
  }

  private def activatedMember(
    activateMemberCommand: ActivateMember,
    newMeta: MemberMetaInfo
  ): Either[Error,MemberResponse] = {
    activeMembers.add(1L)
    Right(
      MemberEventResponse(
        MemberActivated(
          activateMemberCommand.memberId,
          Some(newMeta)
        )
      )
    )
  }

  private def activateMember(
      info: Either[EditableInfo, MemberInfo],
      meta: MemberMetaInfo,
      activateMemberCommand: ActivateMember
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = activateMemberCommand.onBehalfOf,
      lastModifiedOn = Some(Timestamp(Instant.now(clock))),
      currentState = MEMBER_STATE_ACTIVE
    )
    info match {
      case Left(e: EditableInfo) =>
        val validationErrorsOpt = draftTransitionMemberInfoValidator(e)
        if (validationErrorsOpt.isEmpty) {
          activatedMember(activateMemberCommand, newMeta)
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(_: MemberInfo) =>
        activatedMember(activateMemberCommand, newMeta)
    }
  }

  private def suspendMember(
      meta: MemberMetaInfo,
      suspendMemberCommand: SuspendMember,
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = suspendMemberCommand.onBehalfOf,
      lastModifiedOn = Some(Timestamp(Instant.now(clock))),
      currentState = MEMBER_STATE_SUSPENDED
    )
    activeMembers.add(-1L)
    Right(
      MemberEventResponse(
        MemberSuspended(
          suspendMemberCommand.memberId,
          Some(newMeta),
          suspendMemberCommand.suspensionReason
        )
      )
    )
  }

  private def terminateMember(
      meta: MemberMetaInfo,
      terminateMemberCommand: TerminateMember
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = terminateMemberCommand.onBehalfOf,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )
    val event = MemberTerminated(terminateMemberCommand.memberId, Some(newMeta))
    registeredMembers.add(-1L)
    Right(MemberEventResponse(event))
  }

  private def editMemberInfo(
      info: Either[EditableInfo, MemberInfo],
      meta: MemberMetaInfo,
      editMemberInfoCommand: EditMemberInfo
  ): Either[Error, MemberResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = editMemberInfoCommand.onBehalfOf,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )

    editMemberInfoCommand.memberInfo
      .map { editable =>
        val event = MemberInfoEdited(
          editMemberInfoCommand.memberId,
          Some(info match {
            case Right(i: MemberInfo)  => i.updateInfo(editable).toEditable
            case Left(e: EditableInfo) => e.updateInfo(editable)
          }),
          Some(newMeta)
        )
        Right(MemberEventResponse(event))
      }
      .getOrElse(
        Right(
          MemberEventResponse(
            MemberInfoEdited(
              editMemberInfoCommand.memberId,
              None,
              Some(newMeta)
            )
          )
        )
      )
  }

  private def getMemberInfo(
      info: Either[EditableInfo, MemberInfo],
      meta: MemberMetaInfo,
      getMemberInfoQuery: GetMemberInfo,
  ): Either[Error, MemberResponse] = info match {
    case Left(editable) =>
      Right(MemberData(getMemberInfoQuery.memberId, Some(editable.toInfo), Some(meta)))
    case Right(i) => Right(MemberData(getMemberInfoQuery.memberId, Some(i), Some(meta)))
  }
}
