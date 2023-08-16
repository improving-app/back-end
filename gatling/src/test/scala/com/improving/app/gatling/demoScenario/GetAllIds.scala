package com.improving.app.gatling.demoScenario

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http
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

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)
  setUp(
    getAllTenantsScn.inject(injectionProfile),
    getAllOrgsScn.inject(injectionProfile),
    getAllMembersScn.inject(injectionProfile)
  ).protocols(httpProtocol)
}
