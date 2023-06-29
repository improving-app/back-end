package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.Route
import com.github.dockerjava.api.exception.InternalServerErrorException
import com.improving.app.common.domain.{
  EditableAddress,
  EditableContact,
  MemberId,
  OrganizationId,
  PostalCodeMessageImpl,
  TenantId,
  UsPostalCodeImpl
}
import com.improving.app.gateway.api.handlers.TenantGatewayHandler
import com.improving.app.gateway.domain.common.tenantUtil.EstablishedTenantUtil
import com.improving.app.gateway.domain.common.util.{genPhoneNumber, genPostalCode}
import com.improving.app.gateway.domain.demoScenario.{ScenarioStarted, StartScenario}
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
import scala.io.Source
import scala.util.Random

trait DemoScenarioGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  private val firstNamesFile = Source.fromFile("../../domain/common/firstNames.txt")
  private val lastNamesFile = Source.fromFile("../../domain/common/lastNames.txt")
  private val cityStatesFile = Source.fromFile("../../domain/common/fakariaCityStates.txt")
  private val addressesFile = Source.fromFile("../../domain/common/addresses.txt")

  private val firstNames: Seq[String] =
    try firstNamesFile.getLines().toSeq
    finally firstNamesFile.close()

  private val lastNames: Seq[String] =
    try lastNamesFile.getLines().toSeq
    finally lastNamesFile.close()

  private val addresses: Seq[String] =
    try addressesFile.getLines().toSeq
    finally addressesFile.close()

  private val cityStates: Seq[Seq[String]] =
    try cityStatesFile.getLines().toSeq.map(_.split(",").toSeq)
    finally cityStatesFile.close()

  def demoScenarioRoutes(handler: TenantGatewayHandler): Route = logRequestResult("DemoScenarioGateway") {
    pathPrefix("demo") {
      pathPrefix("startScenario") {
        post {
          entity(as[String]) { command =>
            val parsed = StartScenario.fromAscii(command)

            val creatingMemberId = Some(MemberId(UUID.randomUUID().toString))
            val orgId = OrganizationId(UUID.randomUUID().toString)

            val establishTenantResponses: Seq[Future[TenantEstablished]] =
              Random
                .shuffle(firstNames)
                .zip(Random.shuffle(lastNames))
                .zip(Random.shuffle(addresses))
                .zip(Random.shuffle(cityStates))
                .take(parsed.numTenants)
                .map {
                  case (((first, last), address), Seq(city, state)) =>
                    handler
                      .establishTenant(
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
                              Some(
                                EditableAddress(
                                  line1 = Some(address),
                                  line2 = None,
                                  city = Some(city),
                                  stateProvince = Some(state),
                                  country = Some("Fakaria"),
                                  postalCode = Some(
                                    PostalCodeMessageImpl(
                                      UsPostalCodeImpl(genPostalCode)
                                    )
                                  )
                                )
                              ),
                              Some(TenantOrganizationList(Seq(orgId)))
                            )
                          )
                        )
                      )
                  case (((_, _), _), _) =>
                    Future.failed(new InternalServerErrorException("Could not generate all appropriate data"))
                }

            val requestsFut = Future
              .sequence(
                establishTenantResponses
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
