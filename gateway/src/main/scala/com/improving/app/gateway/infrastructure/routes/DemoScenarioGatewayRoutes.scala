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

// TODO: Move to gatling, add event start/end and other user focused routes (see IA-235)
trait DemoScenarioGatewayRoutes extends ErrorAccumulatingCirceSupport with StrictLogging {

  val config: Config

  // def demoScenarioRoutes(
  //    tenantHandler: TenantGatewayHandler,
  //    orgHandler: OrganizationGatewayHandler,
  //    memberHandler: MemberGatewayHandler
  // ): Route =
  //  logRequestResult("DemoScenarioGateway") {
  //    pathPrefix("demo-scenario") {
  //      pathPrefix("start") {
  //        post {
  //          entity(as[String]) { command =>
  //            val orgResponse: Future[Organization] = tenantResponses
  //              .flatMap { tenants =>
  //                (Random
  //                  .shuffle(addresses)
  //                  .zip(Random.shuffle(cityStates))
  //                  .zip(creatingMembersForTenant)
  //                  .head match {
  //                  case ((address, Seq(city, state)), memberForTenant) =>
  //                    orgHandler.establishOrganization(
  //                      EstablishOrganization(
  //                        Some(orgId),
  //                        memberForTenant._1,
  //                        Some(
  //                          EditableOrganizationInfo(
  //                            name = Some("The Demo Corporation"),
  //                            shortName = Some("DemoCorp"),
  //                            tenant = memberForTenant._2,
  //                            isPublic = Some(true),
  //                            address = Some(
  //                              EditableAddress(
  //                                line1 = Some(address),
  //                                line2 = None,
  //                                city = Some(city),
  //                                stateProvince = Some(state),
  //                                country = Some("Fakaria"),
  //                                postalCode = Some(
  //                                  PostalCodeMessageImpl(
  //                                    UsPostalCodeImpl(genPostalCode)
  //                                  )
  //                                )
  //                              )
  //                            ),
  //                            url = Some("demo.corp"),
  //                            logo = None
  //                          )
  //                        )
  //                      )
  //                    )
  //                  case ((_, _), _) => Future.failed(new Exception("failed to match on data for creating org"))
  //                }).flatMap { orgEstablished: OrganizationEstablished =>
  //                  orgHandler
  //                    .activateOrganization(
  //                      ActivateOrganization(orgEstablished.organizationId, creatingMembersForTenant.head._1)
  //                    )
  //                    .map(_ => orgEstablished.toOrganization)
  //                }
  //              }
  //              .map { org =>
  //                logger.debug(s"Org successfully established with $org")
  //                org
  //              }
//
  //            val numMembers = parsed.numMembersPerOrg * parsed.numTenants
  //            val memberResponses: Future[Seq[Member]] = (for {
  //              org <- orgResponse
  //            } yield Future
  //              .sequence(
  //                (creatingMembersForTenant
  //                  .map(_._1) ++ Random
  //                  .shuffle(
  //                    (parsed.numTenants - 1 to numMembers).map(_ => Some(MemberId(UUID.randomUUID().toString)))
  //                  ))
  //                  .zip(
  //                    creatingMembersForTenant.map(tup => (tup._1, tup._2)) ++ Random
  //                      .shuffle(
  //                        (parsed.numTenants - 1 to numMembers).map(_ => tenantIds(Random.nextInt(parsed.numTenants)))
  //                      )
  //                      .flatMap { tenant =>
  //                        val creatingMembers = creatingMembersForTenant.map(_._1) ++ Random
  //                          .shuffle(
  //                            (parsed.numTenants - 1 to numMembers)
  //                              .map(_ =>
  //                                creatingMembersForTenant(creatingMembersForTenant.indexWhere(_._2 == tenant))._1
  //                              )
  //                          )
  //                        creatingMembers.map(member =>
  //                          (
  //                            member,
  //                            tenant
  //                          )
  //                        )
  //                      }
  //                  )
  //                  .zip(
  //                    Random
  //                      .shuffle(firstNames)
  //                      .take(numMembers)
  //                      .zip(Random.shuffle(lastNames).take(numMembers))
  //                  )
  //                  .map { case ((id, (creatingMember, tenant)), (firstName, lastName)) =>
  //                    memberHandler
  //                      .registerMember(
  //                        RegisterMember(
  //                          id,
  //                          Some(
  //                            EditableMemberInfo(
  //                              handle = Some(s"${firstName.take(1)}.${lastName.take(3)}"),
  //                              avatarUrl = Some(s"${org.organizationInfo.get.shortName}.org"),
  //                              firstName = Some(firstName),
  //                              lastName = Some(lastName),
  //                              notificationPreference =
  //                                Some(NotificationPreference.NOTIFICATION_PREFERENCE_APPLICATION),
  //                              contact = Some(
  //                                EditableContact(
  //                                  firstName = Some(firstName),
  //                                  lastName = Some(lastName),
  //                                  emailAddress = Some(
  //                                    s"${firstName.toLowerCase}.${lastName.toLowerCase}@${org.organizationInfo.get.shortName}.org"
  //                                  ),
  //                                  phone = Some(genPhoneNumber),
  //                                  userName = Some(s"${firstName.take(1)}$lastName")
  //                                )
  //                              ),
  //                              organizationMembership = Seq(org.getOrganizationId),
  //                              tenant = tenant
  //                            )
  //                          ),
  //                          creatingMember
  //                        )
  //                      )
  //                      .flatMap { memberRegistered: MemberRegistered =>
  //                        memberHandler
  //                          .activateMember(
  //                            ActivateMember(memberRegistered.memberId, memberRegistered.meta.map(_.getCreatedBy))
  //                          )
  //                          .map(_ => memberRegistered.toMember)
  //                      }
  //                  }
  //              )).flatten
  //              .map(_.map { member =>
  //                logger.debug(s"Member successfully established with $member")
  //                member
  //              })
//
  //            complete {
  //              for {
  //                org <- orgResponse
  //                tenants <- tenantResponses
  //                members <- memberResponses
  //              } yield ScenarioStarted(tenants = tenants, organizations = Seq(org), members = members).toProtoString
  //            }
  //          }
  //        }
  //      }
  //    }
  //  }
}
