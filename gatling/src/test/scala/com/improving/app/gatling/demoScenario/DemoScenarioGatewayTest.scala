package com.improving.app.gatling.demoScenario

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.member.RegisterMember
import com.improving.app.gateway.domain.organization.{ActivateOrganization, EstablishOrganization}
import com.improving.app.gateway.domain.tenant.EstablishTenant
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
  val numMembersPerOrg = 2

  val tenantIds: Seq[Some[TenantId]] = (0 to numTenants).map(_ => Some(TenantId(UUID.randomUUID().toString)))
  val creatingMembersForTenant: Map[Some[MemberId], Some[TenantId]] =
    (0 to numTenants).map(i => Some(MemberId(UUID.randomUUID().toString)) -> tenantIds(i)).toMap
  val orgId: OrganizationId = OrganizationId(UUID.randomUUID().toString)

  val establishTenantRequestsByCreatingMember: Seq[(Option[MemberId], EstablishTenant)] =
    (0 to numTenants / 250).flatMap { _ =>
      genEstablishTenantReqs(
        creatingMembersForTenant.keys.toSeq,
        numTenants,
        orgId
      ).map(req => req.onBehalfOf -> req)
        .groupBy(_._1)
        .toSeq
        .map(tup => tup._1 -> tup._2.map(_._2).head)
    }

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

  val establishOrg: EstablishOrganization =
    genEstablishOrg(creatingMembersForTenant.head, orgId).getOrElse(EstablishOrganization.defaultInstance)

  val establishOrgScn: ScenarioBuilder = scenario(
    s"EstablishOrg-${establishOrg.organizationId.map(_.id).getOrElse("ORGANIZATIONID NOT FOUND")}"
  )
    .exec(
      http("StartScenario - EstablishOrg")
        .post("/organization")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat.toJsonString(establishOrg).replace("\"", "\\\"")}\""""
          )
        )
    )

  val activateOrg: ActivateOrganization = genActivateOrgReqs(establishOrg)

  val activateOrgScn: ScenarioBuilder = scenario(
    s"ActivateOrg-${activateOrg.organizationId.map(_.id).getOrElse("ORGANIZATIONID NOT FOUND")}"
  )
    .exec(
      http("StartScenario - ActivateOrg")
        .post("/organization/activate")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat.toJsonString(activateOrg).replace("\"", "\\\"")}\""""
          )
        )
    )

  val registerMemberByOrgs: Map[MemberId, RegisterMember] =
    genRegisterMembers(numMembersPerOrg, creatingMembersForTenant, establishOrg)
      .map(req => req.memberId.getOrElse(MemberId("ORGID NOT FOUND")) -> req)
      .toMap

  val registerMemberScns: Map[MemberId, ScenarioBuilder] = for {
    (memberId, registerMember) <- registerMemberByOrgs
  } yield memberId -> scenario(
    s"RegisterMember-${registerMember.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND")}"
  ).exec(
    http("StartScenario - RegisterMember")
      .post("/member")
      .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
      .body(
        StringBody(
          s"""\"${JsonFormat.toJsonString(registerMember).replace("\"", "\\\"")}\""""
        )
      )
  )

  val activateMemberScns: Map[MemberId, ScenarioBuilder] = for {
    (memberId, activateMember) <- registerMemberByOrgs.map(memberByOrg =>
      memberByOrg._1 -> genActivateMember(memberByOrg._2)
    )
  } yield memberId -> scenario(s"ActivateMember-${activateMember.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND")}")
    .exec(
      http("StartScenario - ActivateMember")
        .post("/member/activate")
        .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
        .body(
          StringBody(
            s"""\"${JsonFormat.toJsonString(activateMember).replace("\"", "\\\"")}\""""
          )
        )
    )

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)

  setUp(
    establishTenantsScn.toSeq
      .map { establishTup =>
        establishTup._2
          .inject(injectionProfile)
          .andThen {
            activateTenantsScn(establishTup._1).inject(injectionProfile).andThen {
              establishOrgScn.inject(injectionProfile).andThen {
                activateOrgScn.inject(injectionProfile).andThen {
                  registerMemberScns.map { registerMemberTup =>
                    registerMemberTup._2
                      .inject(injectionProfile)
                      .andThen(activateMemberScns(registerMemberTup._1).inject(injectionProfile))
                  }.toSeq: _*
                }
              }
            }
          }
      }: _*
  ).protocols(httpProtocol)
}
