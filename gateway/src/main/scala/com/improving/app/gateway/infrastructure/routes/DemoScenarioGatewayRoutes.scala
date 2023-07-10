package com.improving.app.gateway.infrastructure.routes

import akka.http.scaladsl.server.Directives.{as, complete, entity, logRequestResult, pathPrefix, post}
import akka.http.scaladsl.server.Route
import com.github.dockerjava.api.exception.InternalServerErrorException
import com.improving.app.common.domain.{
  Address,
  EditableAddress,
  EditableContact,
  MemberId,
  OrganizationId,
  PostalCodeMessageImpl,
  TenantId,
  UsPostalCodeImpl
}
import com.improving.app.gateway.api.handlers.TenantGatewayHandler
import com.improving.app.gateway.api.handlers.{OrganizationGatewayHandler, TenantGatewayHandler}
import com.improving.app.gateway.domain.common.orgUtil.EstablishedOrganizationUtil
import com.improving.app.gateway.domain.common.util.{genPhoneNumber, genPostalCode}
import com.improving.app.gateway.domain.demoScenario.{Organization, ScenarioStarted, StartScenario, Tenant}
import com.improving.app.gateway.domain.organization.{
  ActivateOrganization,
  EditableOrganizationInfo,
  EstablishOrganization,
  OrganizationEstablished
}
import com.improving.app.gateway.domain.tenant.{
  ActivateTenant,
  EditableTenantInfo,
  EstablishTenant,
  TenantEstablished,
  TenantOrganizationList
}
import com.improving.app.gateway.domain.tenantUtil.EstablishedTenantUtil
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import scalapb.json4s.JsonFormat

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.Random

trait DemoScenarioGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  private val firstNamesFile = Source.fromResource("firstNames.txt")
  private val lastNamesFile = Source.fromResource("lastNames.txt")
  private val cityStatesFile = Source.fromResource("fakariaCityStates.txt")
  private val addressesFile = Source.fromResource("addresses.txt")

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

  def demoScenarioRoutes(tenantHandler: TenantGatewayHandler, orgHandler: OrganizationGatewayHandler): Route =
    logRequestResult("DemoScenarioGateway") {
      pathPrefix("demo-scenario") {
        pathPrefix("start") {
          post {
            entity(as[String]) { command =>
              val parsed = JsonFormat.fromJsonString[StartScenario](command)

              val creatingMemberId = Some(MemberId(UUID.randomUUID().toString))
              val orgId = OrganizationId(UUID.randomUUID().toString)
              logger.debug(parsed.toProtoString)
              val tenantResponses: Future[Seq[Tenant]] =
                Future
                  .sequence(
                    Random
                      .shuffle(firstNames)
                      .zip(Random.shuffle(lastNames))
                      .zip(Random.shuffle(addresses))
                      .zip(Random.shuffle(cityStates))
                      .take(parsed.numTenants)
                      .map {
                        case (((first, last), address), Seq(city, state)) =>
                          tenantHandler
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
                      .map(_.flatMap { tenantEstablished: TenantEstablished =>
                        tenantHandler
                          .activateTenant(ActivateTenant(tenantEstablished.tenantId, creatingMemberId))
                          .map(_ => tenantEstablished.toTenant)
                      })
                  )

              val orgResponse: Future[Organization] = tenantResponses.flatMap { tenants =>
                (Random
                  .shuffle(addresses)
                  .zip(Random.shuffle(cityStates))
                  .zip(Random.shuffle(tenants.map(_.tenantId)))
                  .head match {
                  case ((address, Seq(city, state)), tenant) =>
                    orgHandler.establishOrganization(
                      EstablishOrganization(
                        Some(orgId),
                        creatingMemberId,
                        Some(
                          EditableOrganizationInfo(
                            name = Some("The Demo Corporation"),
                            shortName = Some("DemoCorp"),
                            tenant = tenant,
                            isPublic = Some(true),
                            address = Some(
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
                            url = Some("demo.corp"),
                            logo = None
                          )
                        )
                      )
                    )
                  case ((_, _), _) => Future.failed(new Exception("failed to match on data for creating org"))
                }).flatMap { orgEstablished: OrganizationEstablished =>
                  orgHandler
                    .activateOrganization(ActivateOrganization(orgEstablished.organizationId, creatingMemberId))
                    .map(_ => orgEstablished.toOrganization)
                }
              }

              complete {
                orgResponse.map { org =>
                  tenantResponses.map { tenants =>
                    ScenarioStarted(tenants = tenants, organizations = Seq(org)).toProtoString
                  }
                }
              }
            }
          }
        }
      }
    }
}
