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
import com.improving.app.gateway.api.handlers.{MemberGatewayHandler, OrganizationGatewayHandler, TenantGatewayHandler}
import com.improving.app.gateway.domain.orgUtil.EstablishedOrganizationUtil
import com.improving.app.gateway.domain.common.util.{genPhoneNumber, genPostalCode}
import com.improving.app.gateway.domain.demoScenario.{Member, Organization, ScenarioStarted, StartScenario, Tenant}
import com.improving.app.gateway.domain.member.{
  ActivateMember,
  EditableMemberInfo,
  MemberRegistered,
  NotificationPreference,
  RegisterMember
}
import com.improving.app.gateway.domain.memberUtil.MemberRegisteredUtil
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

  def demoScenarioRoutes(
      tenantHandler: TenantGatewayHandler,
      orgHandler: OrganizationGatewayHandler,
      memberHandler: MemberGatewayHandler
  ): Route =
    logRequestResult("DemoScenarioGateway") {
      pathPrefix("demo-scenario") {
        pathPrefix("start") {
          post {
            entity(as[String]) { command =>
              val parsed = JsonFormat.fromJsonString[StartScenario](command)

              val tenantIds = (0 to parsed.numTenants).map(_ => Some(TenantId(UUID.randomUUID().toString)))

              val creatingMembersForTenant: Seq[(Some[MemberId], Some[TenantId])] =
                (0 to parsed.numTenants).map(i => Some(MemberId(UUID.randomUUID().toString)) -> tenantIds(i))
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
                      .zip(creatingMembersForTenant.map(_._1))
                      .take(parsed.numTenants)
                      .map {
                        case ((((first, last), address), Seq(city, state)), member) =>
                          tenantHandler
                            .establishTenant(
                              EstablishTenant(
                                Some(TenantId(UUID.randomUUID().toString)),
                                member,
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
                      .zip(creatingMembersForTenant.map(_._1))
                      .map { case (tenantResponse, creatingMember) =>
                        tenantResponse.flatMap { tenantEstablished: TenantEstablished =>
                          tenantHandler
                            .activateTenant(ActivateTenant(tenantEstablished.tenantId, creatingMember))
                            .map(_ => tenantEstablished.toTenant)
                        }
                      }
                  )
                  .map { tenant =>
                    logger.debug(s"Tenant successfully established with $tenant")
                    tenant
                  }

              val orgResponse: Future[Organization] = tenantResponses
                .flatMap { tenants =>
                  (Random
                    .shuffle(addresses)
                    .zip(Random.shuffle(cityStates))
                    .zip(creatingMembersForTenant)
                    .head match {
                    case ((address, Seq(city, state)), memberForTenant) =>
                      orgHandler.establishOrganization(
                        EstablishOrganization(
                          Some(orgId),
                          memberForTenant._1,
                          Some(
                            EditableOrganizationInfo(
                              name = Some("The Demo Corporation"),
                              shortName = Some("DemoCorp"),
                              tenant = memberForTenant._2,
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
                      .activateOrganization(
                        ActivateOrganization(orgEstablished.organizationId, creatingMembersForTenant.head._1)
                      )
                      .map(_ => orgEstablished.toOrganization)
                  }
                }
                .map { org =>
                  logger.debug(s"Org successfully established with $org")
                  org
                }

              val numMembers = parsed.numMembersPerOrg * parsed.numTenants
              val memberResponses: Future[Seq[Member]] = (for {
                org <- orgResponse
              } yield Future
                .sequence(
                  (creatingMembersForTenant
                    .map(_._1) ++ Random
                    .shuffle(
                      (parsed.numTenants - 1 to numMembers).map(_ => Some(MemberId(UUID.randomUUID().toString)))
                    ))
                    .zip(
                      creatingMembersForTenant.map(tup => (tup._1, tup._2)) ++ Random
                        .shuffle(
                          (parsed.numTenants - 1 to numMembers).map(_ => tenantIds(Random.nextInt(parsed.numTenants)))
                        )
                        .flatMap { tenant =>
                          val creatingMembers = creatingMembersForTenant.map(_._1) ++ Random
                            .shuffle(
                              (parsed.numTenants - 1 to numMembers)
                                .map(_ =>
                                  creatingMembersForTenant(creatingMembersForTenant.indexWhere(_._2 == tenant))._1
                                )
                            )
                          creatingMembers.map(member =>
                            (
                              member,
                              tenant
                            )
                          )
                        }
                    )
                    .zip(
                      Random
                        .shuffle(firstNames)
                        .take(numMembers)
                        .zip(Random.shuffle(lastNames).take(numMembers))
                    )
                    .map { case ((id, (creatingMember, tenant)), (firstName, lastName)) =>
                      memberHandler
                        .registerMember(
                          RegisterMember(
                            id,
                            Some(
                              EditableMemberInfo(
                                handle = Some(s"${firstName.take(1)}.${lastName.take(3)}"),
                                avatarUrl = Some(s"${org.organizationInfo.get.shortName}.org"),
                                firstName = Some(firstName),
                                lastName = Some(lastName),
                                notificationPreference =
                                  Some(NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION),
                                contact = Some(
                                  EditableContact(
                                    firstName = Some(firstName),
                                    lastName = Some(lastName),
                                    emailAddress = Some(
                                      s"${firstName.toLowerCase}.${lastName.toLowerCase}@${org.organizationInfo.get.shortName}.org"
                                    ),
                                    phone = Some(genPhoneNumber),
                                    userName = Some(s"${firstName.take(1)}$lastName")
                                  )
                                ),
                                organizationMembership = Seq(org.getOrganizationId),
                                tenant = tenant
                              )
                            ),
                            creatingMember
                          )
                        )
                        .flatMap { memberRegistered: MemberRegistered =>
                          memberHandler
                            .activateMember(
                              ActivateMember(memberRegistered.memberId, memberRegistered.meta.map(_.getCreatedBy))
                            )
                            .map(_ => memberRegistered.toMember)
                        }
                    }
                )).flatten
                .map(_.map { member =>
                  logger.debug(s"Member successfully established with $member")
                  member
                })

              complete {
                for {
                  org <- orgResponse
                  tenants <- tenantResponses
                  members <- memberResponses
                } yield ScenarioStarted(tenants = tenants, organizations = Seq(org), members = members).toProtoString
              }
            }
          }
        }
      }
    }
}
