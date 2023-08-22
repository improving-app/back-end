package com.improving.app.event.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.EventId
import com.improving.app.common.errors.{Error, StateError}
import com.improving.app.event.domain.EventState._
import com.improving.app.event.domain.Validation.{
  draftTransitionEventInfoValidator,
  eventCommandValidator,
  eventQueryValidator
}
import com.improving.app.event.domain.util.{EditableEventInfoUtil, EventInfoUtil}
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

  private[domain] case class InProgressEventState(info: EventInfo, meta: EventMetaInfo)
      extends CreatedEventState
      with DefinedEventState

  private[domain] case class DelayedEventState(info: EventInfo, meta: EventMetaInfo)
      extends CreatedEventState
      with DefinedEventState

  private[domain] case class CancelledEventState(info: EventInfo, meta: EventMetaInfo)
      extends CreatedEventState
      with DefinedEventState
  private[domain] case class PastEventState(info: EventInfo, lastMeta: EventMetaInfo) extends EventState

  trait HasEventId {
    def memberId: Option[EventId]
  }

  def apply(entityTypeHint: String, eventId: String): Behavior[EventEnvelope] =
    Behaviors.setup { context =>
      context.log.info("Starting Event {}", eventId)
      EventSourcedBehavior
        .withEnforcedReplies[EventEnvelope, EventResponse, EventState](
          persistenceId = PersistenceId(entityTypeHint, eventId),
          emptyState = emptyState(),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        // .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Event {} stopped", eventId)
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
              val result: Either[Error, EventResponse] = state match {
                case UninitializedEventState =>
                  command.request match {
                    case createEventCommand: CreateEvent => createEvent(createEventCommand)
                    case _ =>
                      Left(
                        StateError(
                          s"${command.request.productPrefix} command cannot be used on an uninitialized Event"
                        )
                      )
                  }
                case DraftEventState(editableInfo, meta) =>
                  command.request match {
                    case scheduleEventCommand: ScheduleEvent =>
                      scheduleEvent(Left(editableInfo), meta, scheduleEventCommand)
                    case editEventInfoCommand: EditEventInfo =>
                      editEventInfo(Left(editableInfo), meta, editEventInfoCommand)
                    case delayEventCommand: DelayEvent =>
                      delayEvent(Left(editableInfo), meta, delayEventCommand)
                    case cancelEventCommand: CancelEvent =>
                      cancelEvent(Left(editableInfo), meta, cancelEventCommand)
                    case _ =>
                      Left(StateError(s"${command.request.productPrefix} command cannot be used on a draft Event"))
                  }
                case x: DefinedEventState =>
                  x match {
                    case ScheduledEventState(info, meta) =>
                      command.request match {
                        case startEventCommand: StartEvent =>
                          if (Instant.now().getEpochSecond > info.getExpectedStart.seconds)
                            startEvent(info, meta, startEventCommand)
                          else
                            Left(
                              StateError(
                                s"${command.request.productPrefix} command cannot be used before an Event has started"
                              )
                            )
                        case rescheduleEventCommand: RescheduleEvent =>
                          rescheduleEvent(info, meta, rescheduleEventCommand)
                        case delayEventCommand: DelayEvent   => delayEvent(Right(info), meta, delayEventCommand)
                        case cancelEventCommand: CancelEvent => cancelEvent(Right(info), meta, cancelEventCommand)
                        case editEventInfoCommand: EditEventInfo =>
                          editEventInfo(Right(info), meta, editEventInfoCommand)
                        case _ =>
                          Left(
                            StateError(s"${command.request.productPrefix} command cannot be used on a scheduled Event")
                          )
                      }
                    case InProgressEventState(info, meta) =>
                      command.request match {
                        case delayEventCommand: DelayEvent =>
                          delayEvent(Right(info), meta, delayEventCommand)
                        case endEventCommand: EndEvent =>
                          if (Instant.now().getEpochSecond > info.getExpectedEnd.seconds)
                            endEvent(meta, endEventCommand)
                          else
                            Left(
                              StateError(
                                s"${command.request.productPrefix} command cannot be used before an Event has started"
                              )
                            )
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} command cannot be used on an in-progress Event"
                            )
                          )
                      }
                    case DelayedEventState(info, meta) =>
                      command.request match {
                        case startEventCommand: StartEvent =>
                          if (Instant.now().getEpochSecond > info.getExpectedStart.seconds)
                            startEvent(info, meta, startEventCommand)
                          else
                            Left(
                              StateError(
                                s"${command.request.productPrefix} command cannot be used before an Event has started"
                              )
                            )
                        case cancelEventCommand: CancelEvent =>
                          cancelEvent(Right(info), meta, cancelEventCommand)
                        case rescheduleEventCommand: RescheduleEvent =>
                          rescheduleEvent(info, meta, rescheduleEventCommand)
                        case editEventInfoCommand: EditEventInfo =>
                          editEventInfo(Right(info), meta, editEventInfoCommand)
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} command cannot be used on a delayed Event"
                            )
                          )
                      }
                    case CancelledEventState(info, meta) =>
                      command.request match {
                        case rescheduleEventCommand: RescheduleEvent =>
                          rescheduleEvent(info, meta, rescheduleEventCommand)
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} command cannot be used on a cancelled Event"
                            )
                          )
                      }
                    case _ =>
                      Left(StateError(s"Registered member has an invalid state ${x.meta.currentState.productPrefix}"))
                  }
                case _: PastEventState =>
                  command.request match {
                    case _ =>
                      Left(
                        StateError(s"Event is past, no commands available")
                      )
                  }
              }

              result match {
                case Left(error)  => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
                case Right(event) => replyWithResponseEvent(event)
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.message))
          }
        case q: EventQuery =>
          eventQueryValidator(q) match {
            case None =>
              val result: Either[Error, EventResponse] = state match {
                case UninitializedEventState =>
                  command.request match {
                    case _ =>
                      Left(
                        StateError(
                          s"${command.request.productPrefix} query cannot be used on an uninitialized Event"
                        )
                      )
                  }
                case DraftEventState(editableInfo, meta) =>
                  command.request match {
                    case getInfo: GetEventData =>
                      Right(
                        EventData(
                          getInfo.eventId,
                          Some(EventInfoOrEditable(EventInfoOrEditable.Value.Editable(editableInfo))),
                          Some(meta)
                        )
                      )
                    case _ =>
                      Left(StateError(s"${command.request.productPrefix} query cannot be used on a draft Event"))
                  }
                case x: DefinedEventState =>
                  x match {
                    case ScheduledEventState(info, meta) =>
                      command.request match {
                        case getInfo: GetEventData =>
                          Right(
                            EventData(
                              getInfo.eventId,
                              Some(EventInfoOrEditable(EventInfoOrEditable.Value.Info(info))),
                              Some(meta)
                            )
                          )
                        case _ =>
                          Left(
                            StateError(s"${command.request.productPrefix} command cannot be used on a scheduled Event")
                          )
                      }
                    case InProgressEventState(info, meta) =>
                      command.request match {
                        case getInfo: GetEventData =>
                          Right(
                            EventData(
                              getInfo.eventId,
                              Some(EventInfoOrEditable(EventInfoOrEditable.Value.Info(info))),
                              Some(meta)
                            )
                          )
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} query cannot be used on an in-progress Event"
                            )
                          )
                      }
                    case DelayedEventState(info, meta) =>
                      command.request match {
                        case getInfo: GetEventData =>
                          Right(
                            EventData(
                              getInfo.eventId,
                              Some(EventInfoOrEditable(EventInfoOrEditable.Value.Info(info))),
                              Some(meta)
                            )
                          )
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} query cannot be used on a delayed Event"
                            )
                          )
                      }
                    case CancelledEventState(info, meta) =>
                      command.request match {

                        case getInfo: GetEventData =>
                          Right(
                            EventData(
                              getInfo.eventId,
                              Some(EventInfoOrEditable(EventInfoOrEditable.Value.Info(info))),
                              Some(meta)
                            )
                          )
                        case _ =>
                          Left(
                            StateError(
                              s"${command.request.productPrefix} query cannot be used on a cancelled Event"
                            )
                          )
                      }
                  }
                case PastEventState(info, meta) =>
                  command.request match {
                    case getInfo: GetEventData =>
                      Right(
                        EventData(
                          getInfo.eventId,
                          Some(EventInfoOrEditable(EventInfoOrEditable.Value.Info(info))),
                          Some(meta)
                        )
                      )
                    case _ =>
                      Left(
                        StateError(s"${command.request.productPrefix} query cannot be used on a past Event")
                      )
                  }
              }

              result match {
                case Left(error)  => Effect.reply(command.replyTo)(StatusReply.Error(error.message))
                case Right(event) => replyWithResponseEvent(event)
              }
            case Some(errors) => Effect.reply(command.replyTo)(StatusReply.Error(errors.message))
          }
        case EventRequestPB.Empty => Effect.reply(command.replyTo)(StatusReply.Error("Message was not an EventRequest"))
      }
  }

  // EventHandler
  private val eventHandler: (EventState, EventResponse) => EventState = { (state, response) =>
    response match {
      case eventResponse: EventEventResponse =>
        eventResponse.eventEvent match {
          case eventCreatedEvent: EventCreated =>
            state match {
              case UninitializedEventState =>
                DraftEventState(
                  editableInfo = eventCreatedEvent.getInfo,
                  meta = eventCreatedEvent.getMeta
                )
              case _ => state
            }

          case eventScheduledEvent: EventScheduled =>
            state match {
              case DraftEventState(_, _) =>
                ScheduledEventState(
                  info = eventScheduledEvent.getInfo.toInfo,
                  meta = eventScheduledEvent.getMeta
                )
              case _ => state
            }

          case eventRescheduledEvent: EventRescheduled =>
            state match {
              case DraftEventState(editableInfo, _) =>
                ScheduledEventState(
                  info = editableInfo.toInfo,
                  meta = eventRescheduledEvent.getMeta
                )
              case ScheduledEventState(info, _) =>
                ScheduledEventState(
                  info = info,
                  meta = eventRescheduledEvent.getMeta
                )
              case DelayedEventState(info, _) =>
                ScheduledEventState(
                  info = info,
                  meta = eventRescheduledEvent.getMeta
                )
              case CancelledEventState(info, _) =>
                ScheduledEventState(
                  info = info,
                  meta = eventRescheduledEvent.getMeta
                )
              case _ => state
            }

          case eventDelayedEvent: EventDelayed =>
            state match {
              case DraftEventState(editableInfo, _) =>
                DelayedEventState(
                  info = editableInfo.toInfo,
                  meta = eventDelayedEvent.getMeta
                )
              case ScheduledEventState(info, _) =>
                DelayedEventState(
                  info = info,
                  meta = eventDelayedEvent.getMeta
                )
              case InProgressEventState(info, _) =>
                DelayedEventState(
                  info = info,
                  meta = eventDelayedEvent.getMeta
                )
              case _ => state
            }

          case eventCancelledEvent: EventCancelled =>
            state match {
              case DraftEventState(editableInfo, _) =>
                CancelledEventState(
                  info = editableInfo.toInfo,
                  meta = eventCancelledEvent.getMeta
                )
              case ScheduledEventState(info, _) =>
                CancelledEventState(
                  info = info,
                  meta = eventCancelledEvent.getMeta
                )
              case _ => state
            }

          case eventStartedEvent: EventStarted =>
            state match {
              case ScheduledEventState(_, _) =>
                InProgressEventState(info = eventStartedEvent.getInfo, meta = eventStartedEvent.getMeta)
              case DelayedEventState(_, _) =>
                InProgressEventState(info = eventStartedEvent.getInfo, meta = eventStartedEvent.getMeta)
              case _ => state
            }

          case eventEndedEvent: EventEnded =>
            state match {
              case InProgressEventState(info: EventInfo, _) =>
                PastEventState(info = info, lastMeta = eventEndedEvent.getMeta)
              case _ => state
            }

          case eventInfoEdited: EventInfoEdited =>
            state match {
              case _: DraftEventState =>
                DraftEventState(
                  editableInfo = eventInfoEdited.getInfo,
                  meta = eventInfoEdited.getMeta
                )
              case _: ScheduledEventState =>
                ScheduledEventState(
                  info = eventInfoEdited.getInfo.toInfo,
                  meta = eventInfoEdited.getMeta
                )
              case _: DelayedEventState =>
                DelayedEventState(
                  info = eventInfoEdited.getInfo.toInfo,
                  meta = eventInfoEdited.getMeta
                )
              case _ => state
            }
          case _ => state
        }
      case _: EventData        => state
      case EventResponse.Empty => state

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled event $other")
    }
  }

  private def createEvent(
      createEventCommand: CreateEvent
  ): Either[Error, EventResponse] = {
    logger.info(s"creating for id ${createEventCommand.getEventId}")
    val now = Timestamp(Instant.now(clock))
    val newMeta = EventMetaInfo(
      lastModifiedOn = Some(now),
      lastModifiedBy = createEventCommand.onBehalfOf,
      createdOn = Some(now),
      createdBy = createEventCommand.onBehalfOf,
      currentState = EVENT_STATE_DRAFT,
      eventStateInfo = None
    )
    val event = EventCreated(
      createEventCommand.eventId,
      createEventCommand.info,
      Some(newMeta)
    )
    Right(EventEventResponse(event))
  }

  private def scheduleEvent(
      info: Either[EditableEventInfo, EventInfo],
      meta: EventMetaInfo,
      scheduleEventCommand: ScheduleEvent
  ): Either[Error, EventResponse] = {
    val now = Timestamp(Instant.now(clock))
    val newMeta = meta.copy(
      lastModifiedOn = Some(now),
      lastModifiedBy = scheduleEventCommand.onBehalfOf,
      scheduledOn = Some(now),
      scheduledBy = scheduleEventCommand.onBehalfOf,
      currentState = EVENT_STATE_SCHEDULED,
      eventStateInfo = Some(EventStateInfo(EventStateInfo.Value.ScheduledEventInfo(ScheduledEventInfo())))
    )

    info match {
      case Left(e: EditableEventInfo) =>
        val updatedInfo =
          if (scheduleEventCommand.info.isDefined)
            e.updateInfo(scheduleEventCommand.getInfo)
          else e
        val validationErrorsOpt = draftTransitionEventInfoValidator(updatedInfo)
        if (validationErrorsOpt.isEmpty) {
          Right(
            EventEventResponse(
              EventScheduled(
                scheduleEventCommand.eventId,
                Some(updatedInfo),
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(info: EventInfo) =>
        Right(
          EventEventResponse(
            EventScheduled(
              scheduleEventCommand.eventId,
              Some(info.toEditable),
              Some(newMeta)
            )
          )
        )
    }
  }

  private def rescheduleEvent(
      info: EventInfo,
      meta: EventMetaInfo,
      rescheduleEventCommand: RescheduleEvent
  ): Either[Error, EventResponse] = {
    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = rescheduleEventCommand.onBehalfOf,
      scheduledOn = now,
      scheduledBy = rescheduleEventCommand.onBehalfOf,
      currentState = EVENT_STATE_SCHEDULED,
      eventStateInfo = Some(EventStateInfo(EventStateInfo.Value.ScheduledEventInfo(ScheduledEventInfo())))
    )

    Right(
      EventEventResponse(
        EventRescheduled(
          rescheduleEventCommand.eventId,
          Some(info.copy(expectedStart = rescheduleEventCommand.start, expectedEnd = rescheduleEventCommand.end)),
          Some(newMeta)
        )
      )
    )
  }

  private def startEvent(
      info: EventInfo,
      meta: EventMetaInfo,
      startEventCommand: StartEvent
  ): Either[Error, EventResponse] = {
    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = startEventCommand.onBehalfOf,
      currentState = EVENT_STATE_INPROGRESS,
      eventStateInfo = Some(EventStateInfo(EventStateInfo.Value.InProgressEventInfo(InProgressEventInfo(now)))),
      actualStart = now
    )

    Right(
      EventEventResponse(
        EventStarted(
          startEventCommand.eventId,
          Some(info),
          Some(newMeta)
        )
      )
    )
  }

  private def delayEvent(
      info: Either[EditableEventInfo, EventInfo],
      meta: EventMetaInfo,
      delayEventCommand: DelayEvent
  ): Either[Error, EventResponse] = {
    val infoWithDelayedTimes: Either[EditableEventInfo, EventInfo] = info match {
      case Right(e) =>
        Right(
          e.copy(
            expectedStart = e.expectedStart
              .map(start =>
                Timestamp.of(
                  start.seconds + delayEventCommand.getExpectedDuration.seconds,
                  start.nanos + delayEventCommand.getExpectedDuration.nanos
                )
              ),
            expectedEnd = e.expectedEnd.map(end =>
              Timestamp.of(
                end.seconds + delayEventCommand.getExpectedDuration.seconds,
                end.nanos + delayEventCommand.getExpectedDuration.nanos
              )
            )
          )
        )
      case Left(e) =>
        Left(
          e.copy(
            expectedStart = e.expectedStart
              .map(start =>
                Timestamp.of(
                  start.seconds + delayEventCommand.getExpectedDuration.seconds,
                  start.nanos + delayEventCommand.getExpectedDuration.nanos
                )
              ),
            expectedEnd = e.expectedEnd.map(end =>
              Timestamp.of(
                end.seconds + delayEventCommand.getExpectedDuration.seconds,
                end.nanos + delayEventCommand.getExpectedDuration.nanos
              )
            )
          )
        )
    }

    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = delayEventCommand.onBehalfOf,
      scheduledOn = now,
      scheduledBy = delayEventCommand.onBehalfOf,
      currentState = EVENT_STATE_DELAYED,
      eventStateInfo = Some(
        EventStateInfo(
          EventStateInfo.Value.DelayedEventInfo(
            DelayedEventInfo(reason = delayEventCommand.reason, timeStartedOpt = meta.actualStart)
          )
        )
      )
    )

    infoWithDelayedTimes match {
      case Left(e: EditableEventInfo) =>
        val validationErrorsOpt = draftTransitionEventInfoValidator(e)
        if (validationErrorsOpt.isEmpty) {
          Right(
            EventEventResponse(
              EventDelayed(
                delayEventCommand.eventId,
                Some(e.toInfo),
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(info: EventInfo) =>
        Right(
          EventEventResponse(
            EventDelayed(
              delayEventCommand.eventId,
              Some(info),
              Some(newMeta)
            )
          )
        )
    }
  }

  private def cancelEvent(
      info: Either[EditableEventInfo, EventInfo],
      meta: EventMetaInfo,
      cancelEventCommand: CancelEvent
  ): Either[Error, EventResponse] = {
    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = cancelEventCommand.onBehalfOf,
      scheduledOn = now,
      scheduledBy = cancelEventCommand.onBehalfOf,
      currentState = EVENT_STATE_CANCELLED,
      eventStateInfo = Some(
        EventStateInfo(
          EventStateInfo.Value.CancelledEventInfo(
            CancelledEventInfo(reason = cancelEventCommand.reason, timeStartedOpt = meta.actualStart)
          )
        )
      )
    )
    info match {
      case Left(e: EditableEventInfo) =>
        val validationErrorsOpt = draftTransitionEventInfoValidator(e)
        if (validationErrorsOpt.isEmpty) {
          Right(
            EventEventResponse(
              EventCancelled(
                cancelEventCommand.eventId,
                Some(newMeta)
              )
            )
          )
        } else
          Left(StateError(validationErrorsOpt.get.message))
      case Right(_: EventInfo) =>
        Right(
          EventEventResponse(
            EventCancelled(
              cancelEventCommand.eventId,
              Some(newMeta)
            )
          )
        )
    }
  }

  private def endEvent(
      meta: EventMetaInfo,
      endEventCommand: EndEvent
  ): Either[Error, EventResponse] = {
    val now = Some(Timestamp(Instant.now(clock)))
    val newMeta = meta.copy(
      lastModifiedOn = now,
      lastModifiedBy = endEventCommand.onBehalfOf,
      actualEnd = now,
      currentState = EVENT_STATE_PAST,
      eventStateInfo = Some(EventStateInfo(EventStateInfo.Value.PastEventInfo(PastEventInfo(meta.actualStart, now))))
    )
    val event = EventEnded(endEventCommand.eventId, Some(newMeta))
    Right(EventEventResponse(event))
  }

  private def editEventInfo(
      info: Either[EditableEventInfo, EventInfo],
      meta: EventMetaInfo,
      editEventInfoCommand: EditEventInfo
  ): Either[Error, EventResponse] = {
    val newMeta = meta.copy(
      lastModifiedBy = editEventInfoCommand.onBehalfOf,
      lastModifiedOn = Some(Timestamp(Instant.now(clock)))
    )

    editEventInfoCommand.info
      .map { editable =>
        val event = EventInfoEdited(
          editEventInfoCommand.eventId,
          Some(info match {
            case Right(i: EventInfo)        => i.updateInfo(editable).toEditable
            case Left(e: EditableEventInfo) => e.updateInfo(editable)
          }),
          Some(newMeta)
        )
        Right(EventEventResponse(event))
      }
      .getOrElse(
        Right(
          EventEventResponse(
            EventInfoEdited(
              editEventInfoCommand.eventId,
              None,
              Some(newMeta)
            )
          )
        )
      )
  }
}
