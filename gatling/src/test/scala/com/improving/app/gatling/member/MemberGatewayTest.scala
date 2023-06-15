package com.improving.app.gatling.member

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.{MemberInfo, NotificationPreference, RegisterMember}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scalapb.json4s.JsonFormat

import java.util.UUID

class MemberGatewayTest extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val scn: ScenarioBuilder = scenario("MemberGatewayTest")
    .exec(
      http("CreateMember")
        .post("/member")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat.toJsonString(
              RegisterMember(
                MemberId(UUID.randomUUID().toString),
                MemberInfo(
                  handle = "handle",
                  avatarUrl = "avatarUrl",
                  firstName = "firstName",
                  lastName = "lastName",
                  notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL),
                  notificationOptIn = true,
                  contact = Contact(
                    firstName = "firstName",
                    lastName = "lastName",
                    emailAddress = Some("email@email.com"),
                    phone = Some("111-111-1111"),
                    userName = "userName"
                  ),
                  organizationMembership = Seq(OrganizationId(UUID.randomUUID().toString)),
                  tenant = TenantId(UUID.randomUUID().toString)
                ),
                MemberId(UUID.randomUUID().toString)
              )
            ).replace("\"", "\\\"")}\""""
          )
        )
    )

  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
}
