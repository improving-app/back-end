package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, onSuccess, pathPrefix, post}
import akka.http.scaladsl.server.Route
import cats.implicits.toFunctorOps
import com.improving.app.common.domain.{EditableAddress, EditableContact, MemberId, OrganizationId, TenantId}
import com.improving.app.gateway.api.handlers.TenantGatewayHandler
import com.improving.app.gateway.domain.common.tenantUtil.EstablishedTenantUtil
import com.improving.app.gateway.domain.common.util.genPhoneNumber
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
import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Random, Success, Using}

trait DemoScenarioGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  private val firstNamesFile = Source.fromFile("../../domain/common/firstNames.txt")
  private val lastNamesFile = Source.fromFile("../../domain/common/lastNames.txt")

  private val firstNames: Seq[String] =
    try firstNamesFile.getLines().toSeq
    finally firstNamesFile.close()

  private val lastNames: Seq[String] =
    try lastNamesFile.getLines().toSeq
    finally lastNamesFile.close()

  def demoScenarioRoutes(handler: TenantGatewayHandler): Route = logRequestResult("DemoScenarioGateway") {
    pathPrefix("demo") {
      pathPrefix("startScenario") {
        post {
          entity(as[String]) { command =>
            val parsed = StartScenario.fromAscii(command)

            val creatingMemberId = Some(MemberId(UUID.randomUUID().toString))
            val orgId = OrganizationId(UUID.randomUUID().toString)

            val establishTenantRequests: Seq[EstablishTenant] =
              Random
                .shuffle(firstNames)
                .take(parsed.numTenants)
                .zip(Random.shuffle(lastNames).take(parsed.numTenants))
                .map { case (first, last) =>
                  EstablishTenant(
                    Some(TenantId(UUID.randomUUID().toString)),
                    creatingMemberId,
                    Some(
                      EditableTenantInfo(
                        Some("Demo-" + LocalDateTime.now().toString),
                        Some(
                          EditableContact(
                            Some(first),
                            Some(last),
                            Some(s"$first.$last@orgorg.com"),
                            Some(genPhoneNumber),
                            Some(first.take(1) + last)
                          )
                        ),
                        Some(EditableAddress()),
                        Some(TenantOrganizationList(Seq(orgId)))
                      )
                    )
                  )
                }

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
