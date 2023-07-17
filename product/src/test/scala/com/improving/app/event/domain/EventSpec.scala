package com.improving.app.event.domain

import com.improving.app.common.domain.{EventId, MemberId}
import TestData._
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.product.domain.Product._
import com.improving.app.event.domain.EventState._
import com.improving.app.product.domain.Product
import com.improving.app.product.domain.util.{EditableEventInfoUtil, ProductInfoUtil}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant

class EventSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with Matchers {
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[EventEnvelope, EventEvent, EventState](
    system,
    Product("testEntityTypeHint", testEventIdString),
    SerializationSettings.disabled
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Event" when {
    "in the UninitializedDraftState" when {
      "executing StartEvent" should {
        "error for a newly initialized event" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseStartEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "StartEvent command cannot be used on an uninitialized Event"
        }
      }
      "executing ScheduleEvent" should {
        "error for a newly initialized event" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseScheduleEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "ScheduleEvent command cannot be used on an uninitialized Event"
        }
      }
      "executing EndEvent" should {
        "error for a newly initialized event" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEndEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EndEvent command cannot be used on an uninitialized Event"
        }
      }
    }
    "in the DraftEventState" when {
      "executing CreateEvent" should {
        "error for an unauthorized creating user" ignore {
          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCreateEvent.copy(
                onBehalfOf = Some(MemberId("unauthorizedUser"))
              ),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCreateEvent,
              _
            )
          )

          val eventCreated =
            result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventCreated

          eventCreated.eventId shouldBe Some(EventId(testEventIdString))
          eventCreated.info shouldBe Some(baseEventInfo.toEditable)
          eventCreated.meta.map(_.currentState) shouldBe Some(EVENT_STATE_DRAFT)
          eventCreated.meta.flatMap(_.createdBy) shouldBe Some(MemberId("creatingMember"))

          val state = result.stateOfType[DraftEventState]

          state.editableInfo.getEventName shouldBe "Event Name"
          state.editableInfo.getExpectedStart.seconds shouldBe state.editableInfo.getExpectedEnd.seconds - 3600
          state.editableInfo.getDescription shouldBe "This is the description"
          state.editableInfo.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("creatingMember")

        }

        "error for registering the same member" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCreateEvent,
              _
            )
          )
          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCreateEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "CreateEvent command cannot be used on a draft Event"
        }
      }

      "executing ScheduleEvent" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseScheduleEvent.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseScheduleEvent,
              _
            )
          )

          val eventScheduled =
            result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventScheduled

          eventScheduled.eventId shouldBe Some(EventId(testEventIdString))
          eventScheduled.meta.map(_.currentState) shouldBe Some(EVENT_STATE_SCHEDULED)
          eventScheduled.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("schedulingMember")

          val state = result.stateOfType[ScheduledEventState]

          state.info.eventName shouldBe "Event Name"
          state.info.getExpectedStart.seconds shouldBe state.info.getExpectedEnd.seconds - 3600
          state.info.getDescription shouldBe "This is the description"
          state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("schedulingMember")
        }
      }

      "executing RescheduleEvent" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseRescheduleEvent.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseRescheduleEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldEqual "RescheduleEvent command cannot be used on a draft Event"
        }
      }

      "executing StartEvent" should {
        "error for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseStartEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "StartEvent command cannot be used on a draft Event"
        }
      }

      "executing EndEvent" should {
        "error for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEndEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EndEvent command cannot be used on a draft Event"
        }
      }

      "executing EditEvent" should {
        "succeed for an empty edit" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEditEventInfo.copy(info = Some(EditableEventInfo())),
              _
            )
          )

          val eventEdited = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventInfoEdited

          eventEdited.eventId shouldEqual baseEditEventInfo.eventId
          eventEdited.info shouldEqual baseCreateEvent.info
          eventEdited.getMeta.currentState shouldEqual EVENT_STATE_DRAFT
          eventEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }

        "succeed for editing all fields" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEditEventInfo,
              _
            )
          )

          val eventEdited = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventInfoEdited

          eventEdited.eventId shouldEqual baseEditEventInfo.eventId
          eventEdited.info shouldEqual baseEditEventInfo.info
          eventEdited.getMeta.currentState shouldEqual EVENT_STATE_DRAFT
          eventEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }
      }

      "executing DelayEvent" should {
        "succeed for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseDelayEvent,
              _
            )
          )

          val eventDelayed = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventDelayed

          eventDelayed.eventId shouldEqual baseDelayEvent.eventId
          eventDelayed.info shouldEqual Some(expectedDelayedEventInfo)
          eventDelayed.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventDelayed.getMeta.lastModifiedBy shouldEqual Some(MemberId("delayingMember"))
          eventDelayed.getMeta.currentState shouldEqual EVENT_STATE_DELAYED
          eventDelayed.getMeta.eventStateInfo shouldEqual Some(
            EventStateInfo(EventStateInfo.Value.DelayedEventInfo(DelayedEventInfo(baseDelayEvent.reason, None)))
          )
        }
      }

      "executing CancelEvent" should {
        "succeed for a draft event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCancelEvent,
              _
            )
          )

          val eventCancelled = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventCancelled

          eventCancelled.eventId shouldEqual baseCancelEvent.eventId
          eventCancelled.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventCancelled.getMeta.lastModifiedBy shouldEqual Some(MemberId("cancellingMember"))
          eventCancelled.getMeta.currentState shouldEqual EVENT_STATE_CANCELLED
          eventCancelled.getMeta.eventStateInfo shouldEqual Some(
            EventStateInfo(EventStateInfo.Value.CancelledEventInfo(CancelledEventInfo(baseCancelEvent.reason, None)))
          )
        }
      }
    }

    "in the ScheduledEventState" when {
      "executing ScheduleEvent" should {
        "error due to already in ScheduledEventState" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseScheduleEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "ScheduleEvent command cannot be used on a scheduled Event"
        }
      }

      "executing RescheduleEvent" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseRescheduleEvent.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseRescheduleEvent,
              _
            )
          )

          val eventRescheduled =
            result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventRescheduled

          eventRescheduled.eventId shouldBe Some(EventId(testEventIdString))
          eventRescheduled.meta.map(_.currentState) shouldBe Some(EVENT_STATE_SCHEDULED)
          eventRescheduled.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("reschedulingMember")

          val state = result.stateOfType[ScheduledEventState]

          state.info.eventName shouldBe "Event Name"
          state.info.getExpectedStart.seconds shouldBe state.info.getExpectedEnd.seconds - 3600
          state.info.getDescription shouldBe "This is the description"
          state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("reschedulingMember")
        }
      }

      "executing StartEvent" should {
        "error for a scheduled event that has not been expected to start yet" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseStartEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "StartEvent command cannot be used before an Event has started"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEventToStartNow, _))

          eventually {
            Thread.sleep(1000)
          }

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseStartEvent, _))

          val eventStarted =
            result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventStarted

          eventStarted.eventId shouldBe Some(EventId(testEventIdString))
          eventStarted.meta.map(_.currentState) shouldBe Some(EVENT_STATE_INPROGRESS)
          eventStarted.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("startingMember")

          val state = result.stateOfType[InProgressEventState]

          state.info.eventName shouldBe "Event Name"
          state.info.getExpectedStart.seconds shouldBe (state.info.getExpectedEnd.seconds - 3600 +- 1)
          state.info.getDescription shouldBe "This is the description"
          state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.scheduledBy.map(_.id) shouldBe Some("schedulingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("startingMember")
          assert(state.meta.getActualStart.seconds <= Instant.now().getEpochSecond)
          state.meta.eventStateInfo.flatMap(
            _.getInProgressEventInfo.timeStarted.map(_.seconds)
          ) shouldEqual state.meta.actualStart.map(_.seconds)
        }
      }

      "executing DelayEvent" should {
        "succeed for a scheduled event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseDelayEvent,
              _
            )
          )

          val eventDelayed = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventDelayed

          eventDelayed.eventId shouldEqual baseDelayEvent.eventId
          eventDelayed.info shouldEqual Some(expectedDelayedEventInfo)
          eventDelayed.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventDelayed.getMeta.lastModifiedBy shouldEqual Some(MemberId("delayingMember"))
          eventDelayed.getMeta.currentState shouldEqual EVENT_STATE_DELAYED
          eventDelayed.getMeta.eventStateInfo shouldEqual Some(
            EventStateInfo(EventStateInfo.Value.DelayedEventInfo(DelayedEventInfo(baseDelayEvent.reason, None)))
          )
        }
      }

      "executing CancelEvent" should {
        "succeed for a scheduled event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCancelEvent,
              _
            )
          )

          val eventCancelled = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventCancelled

          eventCancelled.eventId shouldEqual baseCancelEvent.eventId
          eventCancelled.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventCancelled.getMeta.lastModifiedBy shouldEqual Some(MemberId("cancellingMember"))
          eventCancelled.getMeta.currentState shouldEqual EVENT_STATE_CANCELLED
          eventCancelled.getMeta.eventStateInfo shouldEqual Some(
            EventStateInfo(EventStateInfo.Value.CancelledEventInfo(CancelledEventInfo(baseCancelEvent.reason, None)))
          )
        }
      }

      "executing EditEvent" should {
        "succeed for editing all fields" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEditEventInfo,
              _
            )
          )

          val eventEdited = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventInfoEdited

          eventEdited.eventId shouldEqual baseEditEventInfo.eventId
          eventEdited.info shouldEqual baseEditEventInfo.info
          eventEdited.getMeta.currentState shouldEqual EVENT_STATE_SCHEDULED
          eventEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }
      }

      "executing commands End or Create" should {
        "error on all commands" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

          val commands = Seq(
            baseCreateEvent,
            baseEndEvent
          )

          commands.map { command =>
            val response = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(command, _)).reply
            println(command.getClass.getName.split('.').last)
            assert(response.isError)
            val responseError = response.getError
            responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on a delayed Event"
          }
        }
      }
    }

    "in the InProgressEventState" when {
      "executing EndEvent" should {
        "error for an in-progress event before expectedEnd" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEndEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldBe "EndEvent command cannot be used on a scheduled Event"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(baseScheduleEventToStartNowAndEndInOneSecond, _)
          )

          eventually {
            Thread.sleep(1000)
          }

          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseStartEvent, _))

          eventually {
            Thread.sleep(1000)
          }

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEndEvent,
              _
            )
          )

          val eventEnded =
            result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventEnded

          eventEnded.eventId shouldBe Some(EventId(testEventIdString))
          eventEnded.meta.map(_.currentState) shouldBe Some(EVENT_STATE_PAST)
          eventEnded.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("endingMember")

          eventually {
            Thread.sleep(5500)
          }

          val state = result.stateOfType[PastEventState]

          state.info.eventName shouldBe "Event Name"
          state.info.getExpectedStart.seconds shouldBe (state.info.getExpectedEnd.seconds - 1 +- 1)
          state.info.getDescription shouldBe "This is the description"
          state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
          state.lastMeta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.lastMeta.scheduledBy.map(_.id) shouldBe Some("schedulingMember")
          state.lastMeta.lastModifiedBy.map(_.id) shouldBe Some("endingMember")
          assert(state.lastMeta.getActualStart.seconds <= Instant.now().getEpochSecond)
          state.lastMeta.eventStateInfo.flatMap(
            _.getPastEventInfo.timeStarted.map(_.seconds)
          ) shouldEqual state.lastMeta.actualStart.map(_.seconds)
          assert(state.lastMeta.getActualEnd.seconds < Instant.now().getEpochSecond)
          state.lastMeta.eventStateInfo.flatMap(
            _.getPastEventInfo.timeEnded.map(_.seconds)
          ) shouldEqual state.lastMeta.actualEnd.map(_.seconds)
        }
      }

      "executing DelayEvent" should {
        "succeed for a in-progress event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEventToStartNow, _))
          val startResult = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseStartEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseDelayEvent,
              _
            )
          )

          val eventDelayed = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventDelayed

          eventDelayed.eventId shouldEqual baseDelayEvent.eventId

          val expectedInfo = baseScheduleEventToStartNow.getInfo
          eventDelayed.info shouldEqual Some(
            baseEventInfo.copy(
              expectedStart = Some(
                Timestamp.of(
                  expectedInfo.getExpectedStart.seconds + baseDelayEvent.getExpectedDuration.seconds,
                  expectedInfo.getExpectedStart.nanos + baseDelayEvent.getExpectedDuration.nanos
                )
              ),
              expectedEnd = Some(
                Timestamp.of(
                  expectedInfo.getExpectedEnd.seconds + baseDelayEvent.getExpectedDuration.seconds,
                  expectedInfo.getExpectedEnd.nanos + baseDelayEvent.getExpectedDuration.nanos
                )
              )
            )
          )
          eventDelayed.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventDelayed.getMeta.lastModifiedBy shouldEqual Some(MemberId("delayingMember"))
          eventDelayed.getMeta.currentState shouldEqual EVENT_STATE_DELAYED
          eventDelayed.getMeta.eventStateInfo shouldEqual Some(
            EventStateInfo(
              EventStateInfo.Value.DelayedEventInfo(
                DelayedEventInfo(
                  baseDelayEvent.reason,
                  startResult.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventStarted.getMeta.actualStart
                )
              )
            )
          )
        }

        "executing commands other than End, Delay or Cancel" should {
          "error on all commands" in {
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEventToStartNow, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseStartEvent, _))

            val commands = Seq(
              baseCreateEvent,
              baseScheduleEvent,
              baseScheduleEvent,
              baseRescheduleEvent,
              baseStartEvent
            )

            commands.map { command =>
              val response = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(command, _)).reply

              assert(response.isError)
              val responseError = response.getError
              responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on an in-progress Event"
            }
          }
        }
      }

      "executing CancelEvent" should {
        "error for an in-progress event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEventToStartNow, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseStartEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCancelEvent,
              _
            )
          )

          result.reply.getError.getMessage shouldEqual "CancelEvent command cannot be used on an in-progress Event"

        }
      }

      "executing commands other than End, Delay, or Cancel" should {
        "error on all commands" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

          val commands = Seq(
            baseCreateEvent,
            baseScheduleEvent,
            baseRescheduleEvent,
            baseStartEvent,
            baseEditEventInfo
          )

          commands.map { command =>
            val response = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(command, _)).reply
            println(command.getClass.getName.split('.').last)
            assert(response.isError)
            val responseError = response.getError
            responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on a delayed Event"
          }
        }
      }
    }

    "in the DelayedEventState" when {
      "executing EditEvent" should {
        "succeed for editing all fields" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseEditEventInfo,
              _
            )
          )

          val eventEdited = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventInfoEdited

          eventEdited.eventId shouldEqual baseEditEventInfo.eventId
          eventEdited.info shouldEqual baseEditEventInfo.info
          eventEdited.getMeta.currentState shouldEqual EVENT_STATE_DELAYED
          eventEdited.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventEdited.getMeta.lastModifiedBy shouldEqual Some(MemberId("editingMember"))
        }

        "executing RescheduleEvent" should {
          "error for an unauthorized registering user" ignore {
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
              EventEnvelope(
                baseRescheduleEvent.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
                _
              )
            )

            result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
          }

          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
              EventEnvelope(
                baseRescheduleEvent,
                _
              )
            )

            val eventRescheduled =
              result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventRescheduled

            eventRescheduled.eventId shouldBe Some(EventId(testEventIdString))
            eventRescheduled.meta.map(_.currentState) shouldBe Some(EVENT_STATE_SCHEDULED)
            eventRescheduled.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("reschedulingMember")

            val state = result.stateOfType[ScheduledEventState]

            state.info.eventName shouldBe "Event Name"
            state.info.getExpectedStart.seconds shouldBe state.info.getExpectedEnd.seconds - 3600
            state.info.getDescription shouldBe "This is the description"
            state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
            state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("reschedulingMember")
          }
        }

        "executing StartEvent" should {
          "error for a delayed event that has not been expected to start yet" in {
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

            val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
              EventEnvelope(
                baseStartEvent,
                _
              )
            )

            result.reply.getError.getMessage shouldBe "StartEvent command cannot be used before an Event has started"
          }

          "succeed for golden path" in {
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseScheduleEventToStartNow, _))
            eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

            eventually {
              Thread.sleep(1000)
            }

            val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseStartEvent, _))

            val eventStarted =
              result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventStarted

            eventStarted.eventId shouldBe Some(EventId(testEventIdString))
            eventStarted.meta.map(_.currentState) shouldBe Some(EVENT_STATE_INPROGRESS)
            eventStarted.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("startingMember")

            val state = result.stateOfType[InProgressEventState]

            state.info.eventName shouldBe "Event Name"
            state.info.getExpectedStart.seconds shouldBe (state.info.getExpectedEnd.seconds - 3600 +- 1)
            state.info.getDescription shouldBe "This is the description"
            state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
            state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
            state.meta.scheduledBy.map(_.id) shouldBe Some("delayingMember")
            state.meta.lastModifiedBy.map(_.id) shouldBe Some("startingMember")
            assert(state.meta.getActualStart.seconds <= Instant.now().getEpochSecond)
            state.meta.eventStateInfo.flatMap(
              _.getInProgressEventInfo.timeStarted.map(_.seconds)
            ) shouldEqual state.meta.actualStart.map(_.seconds)
          }
        }
      }

      "executing CancelEvent" should {
        "succeed for a delayed event" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseCancelEvent,
              _
            )
          )

          val eventCancelled = result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventCancelled

          eventCancelled.eventId shouldEqual baseCancelEvent.eventId
          eventCancelled.getMeta.createdBy shouldEqual Some(MemberId("creatingMember"))
          eventCancelled.getMeta.lastModifiedBy shouldEqual Some(MemberId("cancellingMember"))
          eventCancelled.getMeta.currentState shouldEqual EVENT_STATE_CANCELLED
          eventCancelled.getMeta.eventStateInfo shouldEqual Some(
            EventStateInfo(EventStateInfo.Value.CancelledEventInfo(CancelledEventInfo(baseCancelEvent.reason, None)))
          )
        }
      }

      "executing commands other than Edit, Start, Cancel, or Reschedule" should {
        "error on all commands" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseDelayEvent, _))

          val commands = Seq(
            baseCreateEvent,
            baseScheduleEvent,
            baseDelayEvent,
            baseEndEvent
          )

          commands.map { command =>
            val response = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(command, _)).reply
            println(command.getClass.getName.split('.').last)
            assert(response.isError)
            val responseError = response.getError
            responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on a delayed Event"
          }
        }
      }

    }

    "in the CancelledEventState" when {
      "executing RescheduleEvent" should {
        "error for an unauthorized registering user" ignore {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCancelEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseRescheduleEvent.copy(onBehalfOf = Some(MemberId("unauthorizedUser"))),
              _
            )
          )

          result.reply.getError.getMessage shouldBe "User is not authorized to modify Member"
        }

        "succeed for golden path" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCancelEvent, _))

          val result = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](
            EventEnvelope(
              baseRescheduleEvent,
              _
            )
          )

          val eventRescheduled =
            result.reply.getValue.asMessage.getEventEventValue.eventEvent.asMessage.getEventRescheduled

          eventRescheduled.eventId shouldBe Some(EventId(testEventIdString))
          eventRescheduled.meta.map(_.currentState) shouldBe Some(EVENT_STATE_SCHEDULED)
          eventRescheduled.meta.flatMap(_.lastModifiedBy.map(_.id)) shouldBe Some("reschedulingMember")

          val state = result.stateOfType[ScheduledEventState]

          state.info.eventName shouldBe "Event Name"
          state.info.getExpectedStart.seconds shouldBe state.info.getExpectedEnd.seconds - 3600
          state.info.getDescription shouldBe "This is the description"
          state.info.getSponsoringOrg shouldBe baseEventInfo.getSponsoringOrg
          state.meta.createdBy.map(_.id) shouldBe Some("creatingMember")
          state.meta.lastModifiedBy.map(_.id) shouldBe Some("reschedulingMember")
        }
      }

      "executing commands other than Reschedule" should {
        "error on all commands" in {
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCreateEvent, _))
          eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(baseCancelEvent, _))

          val commands = Seq(
            baseCreateEvent,
            baseScheduleEvent,
            baseDelayEvent,
            baseCancelEvent,
            baseEditEventInfo,
            baseStartEvent,
            baseEndEvent
          )

          commands.map { command =>
            val response = eventSourcedTestKit.runCommand[StatusReply[EventResponse]](EventEnvelope(command, _)).reply

            assert(response.isError)
            val responseError = response.getError
            responseError.getMessage shouldEqual s"${command.getClass.getName.split('.').last} command cannot be used on a cancelled Event"
          }
        }
      }
    }
  }
}
