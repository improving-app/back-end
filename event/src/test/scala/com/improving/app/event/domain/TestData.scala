package com.improving.app.event.domain

import com.google.protobuf.duration.Duration
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{EventId, MemberId, OrganizationId}
import com.improving.app.event.domain.TestData.baseCreateEvent
import com.improving.app.event.domain.TestData.baseCreateEvent.onBehalfOf
import com.improving.app.event.domain.util.EventInfoUtil

import java.time.Instant
import java.util.UUID

object TestData {
  val testEventIdString: String = UUID.randomUUID().toString

  val now: Instant = Instant.now()

  val baseEventInfo: EventInfo = EventInfo(
    eventName = "Event Name",
    description = Some("This is the description"),
    eventUrl = Some("improving.app/url"),
    sponsoringOrg = Some(OrganizationId(UUID.randomUUID().toString)),
    expectedStart = Option(Timestamp.of(now.getEpochSecond + 3600, now.getNano)),
    expectedEnd = Option(Timestamp.of(now.getEpochSecond + 7200, now.getNano)),
    isPrivate = false
  )

  val baseEditableInfo: EditableEventInfo = EditableEventInfo(
    eventName = Some("Edited Event Name"),
    description = Some("This is the description"),
    eventUrl = Some("improving.app/editedUrl"),
    sponsoringOrg = Some(OrganizationId(UUID.randomUUID().toString)),
    expectedStart = Option(Timestamp.of(now.getEpochSecond + 3600, now.getNano)),
    expectedEnd = Option(Timestamp.of(now.getEpochSecond + 7200, now.getNano)),
    isPrivate = false
  )

  val baseCreateEvent: CreateEvent = CreateEvent(
    eventId = Some(EventId(testEventIdString)),
    info = Some(baseEventInfo.toEditable),
    onBehalfOf = Some(MemberId("creatingMember"))
  )

  val baseScheduleEvent: ScheduleEvent = ScheduleEvent(
    eventId = Some(EventId(testEventIdString)),
    onBehalfOf = Some(MemberId("schedulingMember"))
  )

  val baseScheduleEventToStartNow: ScheduleEvent = ScheduleEvent(
    eventId = Some(EventId(testEventIdString)),
    info = Some(
      EditableEventInfo(
        expectedStart = Option(Timestamp.of(now.getEpochSecond, now.getNano)),
        expectedEnd = Option(Timestamp.of(now.getEpochSecond + 3600, now.getNano))
      )
    ),
    onBehalfOf = Some(MemberId("schedulingMember"))
  )

  val baseScheduleEventToStartNowAndEndInOneSecond: ScheduleEvent = ScheduleEvent(
    eventId = Some(EventId(testEventIdString)),
    info = Some(
      EditableEventInfo(
        expectedStart = Option(Timestamp.of(now.getEpochSecond, now.getNano)),
        expectedEnd = Option(Timestamp.of(now.getEpochSecond + 1, now.getNano))
      )
    ),
    onBehalfOf = Some(MemberId("schedulingMember"))
  )

  val baseStartEvent: StartEvent =
    StartEvent(eventId = Some(EventId(testEventIdString)), onBehalfOf = Some(MemberId("startingMember")))

  val baseEndEvent: EndEvent =
    EndEvent(eventId = Some(EventId(testEventIdString)), onBehalfOf = Some(MemberId("endingMember")))

  val baseRescheduleEvent: RescheduleEvent = RescheduleEvent(
    eventId = Some(EventId(testEventIdString)),
    start = Option(Timestamp.of(now.getEpochSecond + 3600, now.getNano)),
    end = Option(Timestamp.of(now.getEpochSecond + 7200, now.getNano)),
    onBehalfOf = Some(MemberId("reschedulingMember"))
  )

  val baseDelayEvent: DelayEvent = DelayEvent(
    eventId = Some(EventId(testEventIdString)),
    reason = "Delay reason",
    expectedDuration = Option(Duration.of(3600, 0)),
    onBehalfOf = Some(MemberId("delayingMember"))
  )

  val baseCancelEvent: CancelEvent = CancelEvent(
    eventId = Some(EventId(testEventIdString)),
    reason = "Cancellation reason",
    onBehalfOf = Some(MemberId("cancellingMember"))
  )

  val baseEditEventInfo: EditEventInfo = EditEventInfo(
    eventId = Some(EventId(testEventIdString)),
    info = Some(baseEditableInfo),
    onBehalfOf = Some(MemberId("editingMember"))
  )

  val expectedDelayedEventInfo: EventInfo = baseEventInfo.copy(
    expectedStart = baseEventInfo.expectedStart
      .map(start =>
        Timestamp.of(
          start.seconds + baseDelayEvent.getExpectedDuration.seconds,
          start.nanos + baseDelayEvent.getExpectedDuration.nanos
        )
      ),
    expectedEnd = baseEventInfo.expectedEnd.map(end =>
      Timestamp.of(
        end.seconds + baseDelayEvent.getExpectedDuration.seconds,
        end.nanos + baseDelayEvent.getExpectedDuration.nanos
      )
    )
  )
}
