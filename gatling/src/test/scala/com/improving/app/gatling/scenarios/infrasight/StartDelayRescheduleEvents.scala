package com.improving.app.gatling.scenarios.infrasight

import akka.http.scaladsl.model.ContentTypes
import com.google.protobuf.duration.Duration
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{EventId, MemberId}
import com.improving.app.common.domain.util.StringUtil
import com.improving.app.gateway.domain.event.{
  AllEventIds,
  DelayEvent,
  EndEvent,
  EventData,
  EventState,
  RescheduleEvent,
  StartEvent
}
import com.improving.app.gateway.domain.organization.AllOrganizationIds
import com.improving.app.common.domain.util.GeneratedMessageUtil
import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.{OpenInjectionBuilder, OpenInjectionStep}
import io.gatling.core.session.Expression
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import org.json4s.{JValue, JsonFormat}
import scalapb.json4s.JsonFormat.{fromJson, fromJsonString}

import java.time.Instant
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

class StartDelayRescheduleEvents extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val getAllOrgs: HttpRequestBuilder =
    http(s"InfrasightLongRunning - GetAllOrgs")
      .get(s"/organization/allIds")

  val getEventsScheduledForOrg: HttpRequestBuilder = http(s"InfrasightLongRunning - GetEventsScheduledForOrg")
    .get(
      s"/event/allData/status/${EventState.EVENT_STATE_SCHEDULED}/forOrg/#{id}"
    )

  val getEventsDelayedForOrg: HttpRequestBuilder = http(s"InfrasightLongRunning - GetEventsDelayedForOrg")
    .get(
      s"/event/allData/status/${EventState.EVENT_STATE_DELAYED}/forOrg/#{id}"
    )

  val getEventsInProgressForOrg: HttpRequestBuilder = http(s"InfrasightLongRunning - GetEventsInProgressForOrg")
    .get(
      s"/event/allData/status/${EventState.EVENT_STATE_INPROGRESS}/forOrg/#{id}"
    )

  val startEvent: HttpRequestBuilder =
    http("InfrasightLongRunning - StartEvent #{id}")
      .post(s"/event/start")
      .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
      .body(StringBody(StartEvent(Some(EventId("#{id}")), Some(MemberId("onBehalf"))).printAsResponse))

  val delayEvent: HttpRequestBuilder =
    http("InfrasightLongRunning - DelayEvent #{id}")
      .post(s"/event/delay")
      .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
      .body(
        StringBody(
          DelayEvent(
            Some(EventId("#{id}")),
            "Reason",
            Some(Duration.of(1, 1)),
            Some(MemberId("onBehalf"))
          ).printAsResponse
        )
      )

  val rescheduleEvent: HttpRequestBuilder = {
    val now = Instant.now()
    http("InfrasightLongRunning - RescheduleEvent #{id}")
      .post(s"/event/reschedule")
      .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
      .body(
        StringBody(
          RescheduleEvent(
            Some(EventId("#{id}")),
            Some(Timestamp.of(now.getEpochSecond, 0)),
            Some(Timestamp.of(now.getEpochSecond + 3600, 0)),
            Some(MemberId("onBehalf"))
          ).printAsResponse
        )
      )
  }

  val getAndSaveAllOrgs: ChainBuilder = exec(getAllOrgs.check(bodyString.saveAs("orgIds")))
    .exec { session =>
      session.set(
        "orgIds",
        fromJsonString[AllOrganizationIds](
          session("orgIds").as[String].forParsingAllIdsProtoResponse
        ).allOrganizationIds.map(_.id)
      )
    }

  val saveEvents: ChainBuilder = exec { session =>
    session.set(
      "eventIds",
      session("eventData")
        .as[Seq[String]]
        .map(data =>
          fromJsonString[EventData](data).eventId
            .getOrElse(EventId.defaultInstance)
            .id
        )
    )
  }

  def commandAllEventsForOrg(eventsForOrg: HttpRequestBuilder, command: HttpRequestBuilder): ChainBuilder =
    exec(eventsForOrg.check(jsonPath("$").ofType[Seq[Any]].saveAs("eventData")))
      .exec(saveEvents)
      .foreach("${eventIds}", "id") {
        exec(command)
      }
      .exec { session =>
        session
          .set("eventIds", "")
          .set("eventData", "")
      }

  val startAllEventsForOrg: ChainBuilder =
    commandAllEventsForOrg(getEventsScheduledForOrg, startEvent)

  val delayAllEventsForOrg: ChainBuilder =
    commandAllEventsForOrg(getEventsInProgressForOrg, delayEvent)

  val rescheduleAllEventsForOrg: ChainBuilder =
    commandAllEventsForOrg(getEventsDelayedForOrg, rescheduleEvent)

  private def getScn(
      repeat: Int,
      myPause: Expression[FiniteDuration]
  ) = scenario(
    "StartDelayRescheduleEvents"
  ).repeat(repeat) {
    exec(getAndSaveAllOrgs)
      .foreach("${orgIds}", "id") {
        exec(startAllEventsForOrg)
      }
      .foreach("${orgIds}", "id") {
        exec(delayAllEventsForOrg)
      }
      .foreach("${orgIds}", "id") {
        exec(rescheduleAllEventsForOrg)
      }
      .pause(myPause)
  }

  val scn: ChainBuilder = exec(getScn(2, 1 nano))

  private val longRunningScn = exec(getScn(100, 5 seconds))
    .exec(getScn(50, 4 seconds))
    .exec(getScn(70, 3 seconds))
    .exec(getScn(120, 2 seconds))
    .exec(getScn(150, 1 seconds))

  private val injectionProfile: OpenInjectionStep = atOnceUsers(1)

  setUp(
    scenario("InfrasightLongRunning")
      .exec(
        scn
      )
      .inject(injectionProfile)
  ).protocols(httpProtocol)

}
