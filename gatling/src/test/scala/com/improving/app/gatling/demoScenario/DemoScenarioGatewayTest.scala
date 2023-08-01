package com.improving.app.gatling.demoScenario

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{EventId, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.event.CreateEvent
import com.improving.app.gateway.domain.member.RegisterMember
import com.improving.app.gateway.domain.organization.{ActivateOrganization, EstablishOrganization}
import com.improving.app.gateway.domain.tenant.EstablishTenant
import com.improving.app.gatling.demoScenario.gen.eventGen.{genCreateEvents, genScheduleEvent}
import com.improving.app.gatling.demoScenario.gen.memberGen.{genActivateMember, genRegisterMembers}
import com.improving.app.gatling.demoScenario.gen.organizationGen.{genActivateOrgReqs, genEstablishOrg}
import com.improving.app.gatling.demoScenario.gen.tenantGen.{genActivateTenantReqs, genEstablishTenantReqs}
import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scalapb.json4s.JsonFormat

import java.util.UUID

class DemoScenarioGatewayTest extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val numTenants = 1
  val numOrgsPerTenant = 1
  val numMembersPerOrg = 300
  val numEventsPerOrg = 10

  val tenantIds: Seq[Option[TenantId]] = (0 until numTenants).map(_ => Some(TenantId(UUID.randomUUID().toString)))
  val tenantsByCreatingMember: Map[Option[MemberId], Option[TenantId]] =
    tenantIds.map(tenant => Some(MemberId(UUID.randomUUID().toString)) -> tenant).toMap
  val orgIdsByCreatingMember: Map[Option[MemberId], OrganizationId] =
    tenantsByCreatingMember.keys.toSeq.map(member => member -> OrganizationId(UUID.randomUUID().toString)).toMap

  val establishTenantRequestsByCreatingMember: Seq[(Option[MemberId], EstablishTenant)] = genEstablishTenantReqs(
    tenantsByCreatingMember.keys.toSeq,
    numTenants,
    orgIdsByCreatingMember
  ).map(req => req.onBehalfOf -> req)
    .groupBy(_._1)
    .toSeq
    .map(tup => tup._1 -> tup._2.map(_._2).head)

  val establishTenantsScn: Map[Option[MemberId], ScenarioBuilder] = (for {
    (creatingMember, tenantReq) <- establishTenantRequestsByCreatingMember
  } yield creatingMember -> scenario(s"EstablishTenant-${tenantReq.tenantId.map(_.id).getOrElse("TENANTID NOT FOUND")}")
    .exec(
      http("StartScenario - EstablishTenants")
        .post("/tenant")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat.toJsonString(tenantReq).replace("\"", "\\\"")}\""""
          )
        )
    )).toMap

  val activateTenantsScn: Map[Option[MemberId], ScenarioBuilder] = (for {
    (creatingMember, tenantReq) <- establishTenantRequestsByCreatingMember.map(tup =>
      tup._1 -> genActivateTenantReqs(tup._2)
    )
  } yield creatingMember -> scenario(s"ActivateTenant-${tenantReq.tenantId.map(_.id).getOrElse("TENANTID NOT FOUND")}")
    .exec(
      http("StartScenario - ActivateTenants")
        .post("/tenant/activate")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat.toJsonString(tenantReq).replace("\"", "\\\"")}\""""
          )
        )
    )).toMap

  val establishOrgs: Seq[(Option[MemberId], EstablishOrganization)] =
    tenantsByCreatingMember.map { case (member, tenant) =>
      member -> genEstablishOrg((member, tenant), orgIdsByCreatingMember(member)).getOrElse(
        EstablishOrganization.defaultInstance
      )
    }.toSeq

  val establishOrgScn: Map[Option[MemberId], ScenarioBuilder] = establishOrgs
    .map(req =>
      req._1 ->
        scenario(
          s"EstablishOrg-${req._2.organizationId.map(_.id).getOrElse("ORGANIZATIONID NOT FOUND")}"
        )
          .exec(
            http("StartScenario - EstablishOrg")
              .post("/organization")
              .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
              .body(
                StringBody(
                  s"""\"${JsonFormat.toJsonString(req._2).replace("\"", "\\\"")}\""""
                )
              )
          )
    )
    .toMap

  val activateOrgs: Seq[(Option[MemberId], ActivateOrganization)] =
    establishOrgs.map(req => req._1 -> genActivateOrgReqs(req._2))

  val activateOrgScn: Map[Option[MemberId], ScenarioBuilder] = activateOrgs
    .map(req =>
      req._1 -> scenario(
        s"ActivateOrg-${req._2.organizationId.map(_.id).getOrElse("ORGANIZATIONID NOT FOUND")}"
      )
        .exec(
          http("StartScenario - ActivateOrg")
            .post("/organization/activate")
            .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
            .body(
              StringBody(
                s"""\"${JsonFormat.toJsonString(req._2).replace("\"", "\\\"")}\""""
              )
            )
        )
    )
    .toMap

  val registerMemberByOrgs: Map[OrganizationId, Seq[RegisterMember]] = tenantsByCreatingMember
    .flatMap { case (member, tenant) =>
      establishOrgs.toMap
        .get(member)
        .map { org =>
          genRegisterMembers(
            numMembersPerOrg,
            (member, establishTenantRequestsByCreatingMember.toMap.get(member)),
            org
          )
            .groupBy(
              _.memberInfo.flatMap(_.organizationMembership.headOption)
            )
        }
        .getOrElse(Map())
    }
    .map(tup => tup._1.getOrElse(OrganizationId.defaultInstance) -> tup._2)

  val registerMemberScns: Map[OrganizationId, Seq[(MemberId, ScenarioBuilder)]] = for {
    (orgId, registerMembers) <- registerMemberByOrgs
  } yield orgId -> registerMembers
    .map(req =>
      req.memberId.getOrElse(MemberId.defaultInstance) ->
        scenario(
          s"RegisterMember-${req.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND")}"
        ).exec(
          http("StartScenario - RegisterMember")
            .post("/member")
            .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
            .body(
              StringBody(
                s"""\"${JsonFormat.toJsonString(req).replace("\"", "\\\"")}\""""
              )
            )
        )
    )

  val activateMemberScns: Map[OrganizationId, Map[MemberId, ScenarioBuilder]] = for {
    (orgId, activateMembers) <- registerMemberByOrgs.map(memberByOrg =>
      memberByOrg._1 -> memberByOrg._2.map(genActivateMember)
    )
  } yield orgId -> activateMembers
    .map(req =>
      req.memberId.getOrElse(MemberId.defaultInstance) -> scenario(
        s"ActivateMember-${req.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND")}"
      )
        .exec(
          http("StartScenario - ActivateMember")
            .post("/member/activate")
            .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
            .body(
              StringBody(
                s"""\"${JsonFormat.toJsonString(req).replace("\"", "\\\"")}\""""
              )
            )
        )
    )
    .toMap

  val createEventsByOrg: Map[OrganizationId, Seq[CreateEvent]] = establishOrgs.map { case (member, org) =>
    org.organizationId.getOrElse(OrganizationId("ORGANIZATION NOT FOUND")) -> genCreateEvents(
      numEventsPerOrg,
      member,
      org
    )
  }.toMap

  val createEventsScns: Map[OrganizationId, Map[EventId, ScenarioBuilder]] = for {
    (orgId, createEvents) <- createEventsByOrg
  } yield orgId -> createEvents
    .map(req =>
      req.eventId.getOrElse(EventId.defaultInstance) -> scenario(
        s"RegisterMember-${req.eventId.map(_.id).getOrElse("EVENTID NOT FOUND")}"
      ).exec(
        http("StartScenario - CreateEvent")
          .post("/event")
          .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
          .body(
            StringBody(
              s"""\"${JsonFormat.toJsonString(req).replace("\"", "\\\"")}\""""
            )
          )
      )
    )
    .toMap

  val scheduleEventsScns: Map[EventId, ScenarioBuilder] = (for {
    (_, scheduleEvent) <- createEventsByOrg.map(tup => tup._1 -> tup._2.map(genScheduleEvent))
  } yield scheduleEvent
    .map(req =>
      req.eventId.getOrElse(EventId("EVENTID NOT FOUND")) ->
        scenario(
          s"ScheduleEvent-${req.eventId.map(_.id).getOrElse("EVENTID NOT FOUND")}"
        ).exec(
          http("StartScenario - ScheduleEvent")
            .post("/event/schedule")
            .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
            .body(
              StringBody(
                s"""\"${JsonFormat.toJsonString(req).replace("\"", "\\\"")}\""""
              )
            )
        )
    )).flatten.toMap

  val getMembers: Map[MemberId, ScenarioBuilder] = (for {
    registerMember <- registerMemberByOrgs.values
  } yield registerMember
    .map(req =>
      req.memberId
        .getOrElse(MemberId.defaultInstance) -> scenario(
        s"GetMemberInfo-${req.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND")}"
      )
        .exec(
          http("StartScenario - GetMemberInfo")
            .get(s"/member/${req.memberId.getOrElse(MemberId("MEMBERID NOT FOUND")).id}")
            .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        )
    )).flatten.toMap

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)
  setUp(
    establishTenantsScn.toSeq
      .map { establishTup =>
        establishTup._2
          .inject(injectionProfile)
          .andThen {
            activateTenantsScn(establishTup._1).inject(injectionProfile).andThen {
              establishOrgScn(establishTup._1).inject(injectionProfile).andThen {
                activateOrgScn(establishTup._1).inject(injectionProfile).andThen {
                  val orgId = establishOrgs.toMap
                    .get(establishTup._1)
                    .flatMap(_.organizationId)
                    .getOrElse(OrganizationId.defaultInstance)
                  registerMemberScns(
                    orgId
                  ).map(registerMemberIDTup =>
                    registerMemberIDTup._2
                      .inject(injectionProfile)
                      .andThen(
                        activateMemberScns(orgId)(registerMemberIDTup._1)
                          .inject(injectionProfile)
                          .andThen(getMembers(registerMemberIDTup._1).inject(injectionProfile))
                      )
                  ) ++ createEventsScns(orgId).map { createEventOrgIDTup =>
                    createEventOrgIDTup._2
                      .inject(injectionProfile)
                      .andThen(scheduleEventsScns(createEventOrgIDTup._1).inject(injectionProfile))
                  }.toSeq
                }
              }
            }
          }
      }: _*
  ).protocols(httpProtocol)
}
