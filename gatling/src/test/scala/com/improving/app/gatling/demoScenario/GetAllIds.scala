package com.improving.app.gatling.demoScenario

import com.improving.app.gateway.domain.event.EventState
import com.improving.app.gateway.domain.organization.AllOrganizationIds
import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import scalapb.json4s.JsonFormat.fromJsonString

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class GetAllIds extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  def getAllForService(serviceName: String): HttpRequestBuilder =
    http(s"StartScenario - GetAll${serviceName.capitalize}s")
      .get(s"/$serviceName/allIds")
      .check(bodyString.saveAs(s"${serviceName}Ids"))

  val getAllTenants: HttpRequestBuilder = getAllForService("tenant")
  val getAllOrgs: HttpRequestBuilder = getAllForService("organization")
  val getAllMembers: HttpRequestBuilder = getAllForService("member")
  val getAllEvents: HttpRequestBuilder = getAllForService("event")
  val getAllStores: HttpRequestBuilder = getAllForService("store")
  val getAllProducts: HttpRequestBuilder = http(s"StartScenario - AllSkus")
    .get(s"/product/allSkus")
    .check(bodyString.saveAs("skus"))

  val getAllEventsScheduled: HttpRequestBuilder = http(s"StartScenario - GetAllEventsSched")
    .get(s"/event/allData/status/${EventState.EVENT_STATE_SCHEDULED}")

  val getAllEventsScn: ScenarioBuilder = getAllScnForService("event")

  val getAllStoresScn: ScenarioBuilder = getAllScnForService("store")

  val getAllProductsScn: ScenarioBuilder = scenario(
    s"GetAllProducts"
  ).exec(
    http(s"StartScenario - GetAllProducts")
      .get(s"/product/allSkus")
  )

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)

  var numEvents = 0

  setUp(
    scenario("GetAllScenario")
      .exec(
        exec(getAllTenants),
        exec(getAllOrgs),
        exec(getAllMembers),
        exec(getAllEvents),
        exec(getAllEventsScheduled),
        exec(getAllStores),
        exec(getAllProducts),
      )
      .exec { session =>
        val orgIds = fromJsonString[AllOrganizationIds](
          session("organizationIds").as[String].replace("\"", "").replace("\\", "\"")
        ).allOrganizationIds

        session.set("orgId", orgIds.head)
      }
      .exec(
        exec(
          http(s"StartScenario - GetAllEventsSchedForOrg")
            .get(
              s"/event/allData/status/${EventState.EVENT_STATE_SCHEDULED}/forOrg/#{orgId}"
            )
        )
      )
      .inject(injectionProfile)
  ).protocols(httpProtocol)

}
