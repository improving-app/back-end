package com.improving.app.gatling.scenarios.infrasight

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{EventId, MemberId}
import com.improving.app.common.domain.util.StringUtil
import com.improving.app.gateway.domain.event.{AllEventIds, EndEvent, EventData, EventState, StartEvent}
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

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

class StartEndEvents extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val getAllOrgs: HttpRequestBuilder =
    http(s"InfrasightLongRunning - GetAllOrgs")
      .get(s"/organization/allIds")

  val getEventsScheduledForOrg: HttpRequestBuilder = http(s"InfrasightLongRunning - GetEventsScheduledForOrg")
    .get(
      s"/event/allData/status/${EventState.EVENT_STATE_SCHEDULED}/forOrg/#{id}"
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

  val endEvent: HttpRequestBuilder =
    http("InfrasightLongRunning - EndEvent #{id}")
      .post(s"/event/end")
      .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
      .body(StringBody(EndEvent(Some(EventId("#{id}")), Some(MemberId("onBehalf"))).printAsResponse))

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

  val startAllEventsForOrg: ChainBuilder =
    exec(getEventsScheduledForOrg.check(jsonPath("$").ofType[Seq[Any]].saveAs("eventData")))
      .exec(saveEvents)
      .foreach("${eventIds}", "id") {
        exec(startEvent)
      }

  val endAllEventsForOrg: ChainBuilder =
    exec(getEventsInProgressForOrg.check(jsonPath("$").ofType[Seq[Any]].saveAs("eventData")))
      .exec(saveEvents)
      .foreach("${eventIds}", "id") {
        exec(endEvent)
      }

  private def getScn(
      repeat: Int,
      myPause: Expression[FiniteDuration]
  ) = scenario(
    "Query MemberByDateTime Query Scenario Init"
  ).repeat(repeat) {
    exec(getAndSaveAllOrgs)
      .foreach("${orgIds}", "id") {
        exec(startAllEventsForOrg).exec(endAllEventsForOrg)
      }
      .pause(myPause)
  }

  val scn: ChainBuilder = exec(getScn(1, 1 seconds))

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
