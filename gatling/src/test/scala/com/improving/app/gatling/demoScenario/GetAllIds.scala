package com.improving.app.gatling.demoScenario

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder

class GetAllIds extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val getAllMembersScn: ScenarioBuilder = scenario(
    s"GetAllMembers"
  ).exec(
    http("StartScenario - GetAllMembers")
      .get("/member/allIds")
  )

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)
  setUp(
    getAllMembersScn.inject(injectionProfile)
  ).protocols(httpProtocol)
}
