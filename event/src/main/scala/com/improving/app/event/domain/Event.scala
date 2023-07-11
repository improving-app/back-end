package com.improving.app.event.domain

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
import com.improving.app.member.domain.Validation.{
  draftTransitionMemberInfoValidator,
  memberCommandValidator,
  memberQueryValidator
}
import com.improving.app.member.domain.util.{EditableMemberInfoUtil, MemberInfoUtil}
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}

object Event extends StrictLogging {

  private val clock: Clock = Clock.systemDefaultZone()

  val EventEntityKey: EntityTypeKey[EventEnvelope] = EntityTypeKey[EventEnvelope]("Event")

  // Command wraps the request type
  final case class EventEnvelope(request: EventRequestPB, replyTo: ActorRef[StatusReply[EventResponse]])

  private def emptyState(): EventState = {
    UninitializedEventState
  }
  EVENT_STATE_DRAFT
  EVENT_STATE_SCHEDULED
  EVENT_STATE_INPROGRESS
  EVENT_STATE_DELAYED
  EVENT_STATE_PAST
  EVENT_STATE_CANCELLED
  sealed private[domain] trait EventState
  private[domain] object UninitializedEventState extends EventState
  sealed private[domain] trait CreatedEventState extends EventState {
    val meta: EventMetaInfo
  }

  sealed private[domain] trait DefinedEventState extends EventState {
    val info: EventInfo
    val meta: EventMetaInfo
  }

  private[domain] case class DraftEventState(editableInfo: EditableEventInfo, meta: EventMetaInfo)
      extends CreatedEventState
  private[domain] case class ScheduledEventState(info: EventInfo, meta: EventMetaInfo)
      extends CreatedEventState
      with DefinedEventState
  private[domain] case class TerminatedMemberState(lastMeta: EventMetaInfo) extends EventState

  trait HasMemberId {
    def memberId: Option[MemberId]
  }

  def apply(entityTypeHint: String, memberId: String): Behavior[EventEnvelope] =
    Behaviors.setup { context =>
      context.log.info("Starting Member {}", memberId)
      EventSourcedBehavior
        .withEnforcedReplies[EventEnvelope, EventResponse, EventState](
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
  private val commandHandler: (EventState, EventEnvelope) => ReplyEffect[EventResponse, EventState] = {
    (state, command) =>
      def replyWithResponseEvent(response: EventResponse): ReplyEffect[EventResponse, EventState] = response match {
        case eventResponse: EventEventResponse =>
          Effect
            .persist(eventResponse)
            .thenReply(command.replyTo) { _: EventState => StatusReply.Success(response) }
        case queryResponse: EventData =>
          Effect
            .persist(queryResponse)
            .thenReply(command.replyTo) { _: EventState => StatusReply.Success(response) }
        case _ =>
          Effect.reply(command.replyTo)(
            StatusReply.Error(s"${response.productPrefix} is not a supported event response")
          )
      }
      command.request match {
        case c: EventCommand =>
          eventCommandValidator(c) match {
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
                case x: DefinedEventState =>
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
                    case x: DefinedEventState =>
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
                case _ => Effect.reply(command.replyTo)(StatusReply.Error("Message was not a StoreRequest"))
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.toString))
          }
        case MemberRequestPB.Empty => Effect.reply(command.replyTo)(StatusReply.Error("Message was not a StoreRequest"))
      }
  }

  // EventHandler
  private val eventHandler: (EventState, EventResponse) => EventState = { (state, response) =>
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
              case x: DefinedEventState =>
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
    Right(MemberEventResponse(event))
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
          Right(
            MemberEventResponse(
              MemberActivated(
                activateMemberCommand.memberId,
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(_: MemberInfo) =>
        Right(
          MemberEventResponse(
            MemberActivated(
              activateMemberCommand.memberId,
              Some(newMeta)
            )
          )
        )
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
