package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain.{EventId, MemberId}
import com.improving.app.gateway.domain.event.{CreateEvent, EditableEventInfo, ScheduleEvent}
import com.improving.app.gateway.domain.organization.EstablishOrganization
import com.improving.app.gatling.common.gen._

import java.util.UUID
import scala.util.Random

object eventGen {
  def genCreateEvents(
      numEventsPerOrg: Int,
      creatingMember: Option[MemberId],
      establishOrg: EstablishOrganization
  ): Seq[CreateEvent] = Random
    .shuffle((0 until numEventsPerOrg).map(_ => EventId(UUID.randomUUID().toString)))
    .zip(
      Random
        .shuffle(
          (0 until numEventsPerOrg).map(_ => creatingMember)
        )
        .zip(
          repeatListUntilNAndShuffle(numEventsPerOrg, events).map(eventName =>
            s"$eventName ${UUID.randomUUID().toString.take(4)}"
          )
        )
        .map { case (creatingMember, eventName) =>
          (
            creatingMember,
            eventName,
            s"${establishOrg.organizationInfo.flatMap(_.name).getOrElse("ORGANIZATION NOT FOUND")} is hosting an event! " +
              s"Come attend the $eventName",
            establishOrg
          )
        }
    )
    .zip(
      Random
        .shuffle(
          (0 until numEventsPerOrg).map(_ => genRandomStartAndEndAfterNow)
        )
    )
    .flatMap {
      case (
            (id, (creatingMember, eventName, description, establishOrg)),
            (expectedStart, expectedEnd)
          ) =>
        Some(
          CreateEvent(
            Some(id),
            Some(
              EditableEventInfo(
                eventName = Some(eventName),
                description = Some(description),
                eventUrl = Some(
                  s"$eventName@${establishOrg.organizationInfo.flatMap(_.url).getOrElse("NOTFOUNDDURINGGENERATION.com")}"
                ),
                sponsoringOrg = establishOrg.organizationId,
                expectedStart = Some(expectedStart),
                expectedEnd = Some(expectedEnd),
              )
            ),
            creatingMember
          )
        )
    }

  def genScheduleEvent(createEvent: CreateEvent): ScheduleEvent =
    ScheduleEvent(createEvent.eventId, None, createEvent.onBehalfOf)
}
