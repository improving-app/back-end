package com.improving.app.gatling.demoScenario

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.domain.demoScenario.{StartScenario, Tenant}
import com.improving.app.gateway.domain.tenant.EstablishTenant
import com.improving.app.gatling.demoScenario.util.{genActivateTenantReqs, genEstablishTenantReqs}
import com.typesafe.config.{Config, ConfigFactory}
import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scalapb.json4s.JsonFormat

import java.util.UUID
import scala.concurrent.Future

class DemoScenarioGatewayTest extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val numTenants = 1
  val numOrgsPerTenant = 1
  val numMembersPerOrg = 2

  val tenantIds: Seq[Some[TenantId]] = (0 to numTenants).map(_ => Some(TenantId(UUID.randomUUID().toString)))
  val creatingMembersForTenant: Seq[(Some[MemberId], Some[TenantId])] =
    (0 to numTenants).map(i => Some(MemberId(UUID.randomUUID().toString)) -> tenantIds(i))
  val orgId: OrganizationId = OrganizationId(UUID.randomUUID().toString)

  val establishTenantRequestsByCreatingMember: Seq[(Option[MemberId], EstablishTenant)] =
    (0 to numTenants / 250).flatMap { _ =>
      genEstablishTenantReqs(
        creatingMembersForTenant,
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

  val injectionProfile: OpenInjectionStep = atOnceUsers(1)

  setUp(
    establishTenantsScn.toSeq
      .map { establishTup =>
        establishTup._2
          .inject(injectionProfile)
          .andThen {
            activateTenantsScn(establishTup._1).inject(injectionProfile)
          }
      }: _*
  ).protocols(httpProtocol)
}
