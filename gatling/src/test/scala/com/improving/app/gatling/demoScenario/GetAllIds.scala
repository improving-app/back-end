package com.improving.app.gatling.demoScenario

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

class GetAllIds extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  def getAllScnForService(serviceName: String): ScenarioBuilder = scenario(
    s"GetAll${serviceName.capitalize}s"
  ).exec(
    http(s"StartScenario - GetAll${serviceName.capitalize}s")
      .get(s"/$serviceName/allIds")
  )

  val getAllTenantsScn: ScenarioBuilder = getAllScnForService("tenant")

  val getAllOrgsScn: ScenarioBuilder = getAllScnForService("organization")

  val getAllMembersScn: ScenarioBuilder = getAllScnForService("member")

  val getAllEventsScn: ScenarioBuilder = scenario(
    s"GetAllEvents"
  ).exec(
    http(s"StartScenario - GetAllEvents")
      .get(s"/event/allData")
  )

  val getAllStoresScn: ScenarioBuilder = getAllScnForService("store")

  val getAllProductsScn: ScenarioBuilder = scenario(
    s"GetAllProducts"
  ).exec(
    http(s"StartScenario - GetAllProducts")
      .get(s"/product/allSkus")
  )

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)
  setUp(
    getAllTenantsScn.inject(injectionProfile),
    getAllOrgsScn.inject(injectionProfile),
    getAllMembersScn.inject(injectionProfile),
    getAllEventsScn.inject(injectionProfile),
    getAllStoresScn.inject(injectionProfile),
    getAllProductsScn.inject(injectionProfile),
  ).protocols(httpProtocol)
}
