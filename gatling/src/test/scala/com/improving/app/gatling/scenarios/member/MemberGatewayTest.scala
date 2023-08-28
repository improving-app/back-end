package com.improving.app.gatling.scenarios.member

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{EditableContact, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.member.{EditableMemberInfo, NotificationPreference, RegisterMember}
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
            s"""\"${JsonFormat
                .toJsonString(
                  RegisterMember(
                    Some(MemberId(UUID.randomUUID().toString)),
                    Some(
                      EditableMemberInfo(
                        handle = Some("handle"),
                        avatarUrl = Some("avatarUrl"),
                        firstName = Some("firstName"),
                        lastName = Some("lastName"),
                        notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL),
                        contact = Some(
                          EditableContact(
                            firstName = Some("firstName"),
                            lastName = Some("lastName"),
                            emailAddress = Some("email@email.com"),
                            phone = Some("111-111-1111"),
                            userName = Some("userName")
                          )
                        ),
                        organizationMembership = Seq(OrganizationId(UUID.randomUUID().toString)),
                        tenant = Some(TenantId(UUID.randomUUID().toString))
                      )
                    ),
                    Some(MemberId(UUID.randomUUID().toString))
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
