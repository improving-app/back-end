package com.improving.app.gatling.member

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.gateway.domain.demoScenario.StartScenario
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scalapb.json4s.JsonFormat

import java.util.UUID

class DemoScenarioGatewayTest extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val scn: ScenarioBuilder = scenario("DemoScenarioGatewayTest")
    .exec(
      http("StartScenario")
        .post("/demo-scenario/start")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat
                .toJsonString(
                  StartScenario(
                    numTenants = 1,
                    numMembersPerOrg = 1
                  )
                )
                .replace("\"", "\\\"")}\""""
          )
        )
    )

  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
}
