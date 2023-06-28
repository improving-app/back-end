package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, onSuccess, pathPrefix, post}
import akka.http.scaladsl.server.Route
import com.improving.app.common.domain.{EditableAddress, EditableContact, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.api.handlers.TenantGatewayHandler
import com.improving.app.gateway.domain.common.tenantUtil.EstablishedTenantUtil
import com.improving.app.gateway.domain.demoScenario.{ScenarioStarted, StartScenario, Tenant}
import com.improving.app.gateway.domain.tenant.{
  EditableTenantInfo,
  EstablishTenant,
  TenantEstablished,
  TenantOrganizationList
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait DemoScenarioGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  def demoScenarioRoutes(handler: TenantGatewayHandler): Route = logRequestResult("DemoScenarioGateway") {
    pathPrefix("demo") {
      pathPrefix("startScenario") {
        post {
          entity(as[String]) { command =>
            val parsed = StartScenario.fromAscii(command)

            val creatingMemberId = Some(MemberId(UUID.randomUUID().toString))
            val orgId = OrganizationId(UUID.randomUUID().toString)

            val establishTenantRequests = (1 to parsed.numTenants).map(_ =>
              EstablishTenant(
                Some(TenantId(UUID.randomUUID().toString)),
                creatingMemberId,
                Some(
                  EditableTenantInfo(
                    Some("Demo-" + LocalDateTime.now().toString),
                    Some(
                      EditableContact(
                      )
                    ),
                    Some(EditableAddress()),
                    Some(TenantOrganizationList(Seq(orgId)))
                  )
                )
              )
            )

            val requestsFut = Future
              .sequence(
                establishTenantRequests
                  .map(req =>
                    handler
                      .establishTenant(
                        req
                      )
                  )
              )

            complete {
              requestsFut.map { reqs =>
                ScenarioStarted(tenants = reqs.map(_.toTenant)).toProtoString
              }
            }
          }
        }
      }
    }
  }
}
