package com.improving.app.gatling.demoScenario

import akka.http.scaladsl.model.ContentTypes
import com.improving.app.common.domain.{EventId, MemberId, OrganizationId, Sku, StoreId, TenantId}
import com.improving.app.gateway.domain.event.{CreateEvent, ScheduleEvent}
import com.improving.app.gateway.domain.member.{ActivateMember, GetMemberInfo, RegisterMember}
import com.improving.app.gateway.domain.organization.{ActivateOrganization, EstablishOrganization}
import com.improving.app.gateway.domain.product.{ActivateProduct, CreateProduct}
import com.improving.app.gateway.domain.store.{CreateStore, MakeStoreReady}
import com.improving.app.gateway.domain.tenant.{ActivateTenant, EstablishTenant}
import com.improving.app.gatling.demoScenario.gen.eventGen.{genCreateEvents, genScheduleEvent}
import com.improving.app.gatling.demoScenario.gen.memberGen.{genActivateMember, genRegisterMembers}
import com.improving.app.gatling.demoScenario.gen.organizationGen.{genActivateOrgReqs, genEstablishOrg}
import com.improving.app.gatling.demoScenario.gen.productGen.{genActivateProduct, genCreateProducts}
import com.improving.app.gatling.demoScenario.gen.storeGen.{genCreateStores, genMakeStoreReady}
import com.improving.app.gatling.demoScenario.gen.tenantGen.{genActivateTenantReqs, genEstablishTenantReqs}
import io.gatling.core.Predef._
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

import java.util.UUID

class DemoScenarioGatewayStart extends Simulation {
  val httpProtocol: HttpProtocolBuilder = http.baseUrl("http://localhost:9000")

  val numTenants = 2
  val numOrgsPerTenant = 1
  val numMembersPerOrg = 2
  val numEventsPerOrg = 2
  val numStoresPerEvent = 2
  val numProductsPerEventStore = 2

  val tenantIds: Seq[Option[TenantId]] = (0 until numTenants).map(_ => Some(TenantId(UUID.randomUUID().toString)))
  val tenantsByCreatingMember: Map[Option[MemberId], Option[TenantId]] =
    tenantIds.map(tenant => Some(MemberId(UUID.randomUUID().toString)) -> tenant).toMap
  val orgIdsByCreatingMember: Map[Option[MemberId], OrganizationId] =
    tenantsByCreatingMember.keys.toSeq.map(member => member -> OrganizationId(UUID.randomUUID().toString)).toMap

  def createScn[T <: GeneratedMessage](scnId: String, path: String, req: T): ScenarioBuilder = scenario(
    s"${req.getClass.getSimpleName}-$scnId"
  ).exec(
    http(s"StartScenario - ${req.getClass.getSimpleName}")
      .post(path)
      .headers(Map("Content-Type" -> ContentTypes.`application/json`.toString()))
      .body(
        StringBody(
          s"""\"${JsonFormat.toJsonString(req).replace("\"", "\\\"")}\""""
        )
      )
  )

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
  } yield creatingMember -> createScn[EstablishTenant](
    tenantReq.tenantId.map(_.id).getOrElse("TENANTID NOT FOUND"),
    "/tenant",
    tenantReq
  )).toMap

  val activateTenantsScn: Map[Option[MemberId], ScenarioBuilder] = (for {
    (creatingMember, tenantReq) <- establishTenantRequestsByCreatingMember.map(tup =>
      tup._1 -> genActivateTenantReqs(tup._2)
    )
  } yield creatingMember -> createScn[ActivateTenant](
    tenantReq.tenantId.map(_.id).getOrElse("TENANTID NOT FOUND"),
    "/tenant/activate",
    tenantReq
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
        createScn[EstablishOrganization](
          req._2.organizationId.map(_.id).getOrElse("ORGANIZATIONID NOT FOUND"),
          "/organization",
          req._2
        )
    )
    .toMap

  val activateOrgScn: Map[Option[MemberId], ScenarioBuilder] = establishOrgs.map { req =>
    val activate = genActivateOrgReqs(req._2)
    req._1 -> createScn[ActivateOrganization](
      activate.organizationId.map(_.id).getOrElse("ORGANIZATIONID NOT FOUND"),
      "/organization/activate",
      activate
    )
  }.toMap

  val registerMemberByOrgs: Map[OrganizationId, Seq[RegisterMember]] = tenantsByCreatingMember
    .flatMap { case (member, _) =>
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
      req.memberId.getOrElse(MemberId.defaultInstance) -> createScn[RegisterMember](
        req.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND"),
        "/member",
        req
      )
    )

  val activateMemberScns: Map[OrganizationId, Map[MemberId, ScenarioBuilder]] = for {
    (orgId, activateMembers) <- registerMemberByOrgs.map(memberByOrg =>
      memberByOrg._1 -> memberByOrg._2.map(genActivateMember)
    )
  } yield orgId -> activateMembers
    .map(req =>
      req.memberId.getOrElse(MemberId.defaultInstance) -> createScn[ActivateMember](
        req.memberId.map(_.id).getOrElse("MEMBERID NOT FOUND"),
        "/member/activate",
        req
      )
    )
    .toMap

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
      req.eventId.getOrElse(EventId.defaultInstance) -> createScn[CreateEvent](
        req.eventId.map(_.id).getOrElse("EVENTID NOT FOUND"),
        s"/event",
        req
      )
    )
    .toMap

  val scheduleEventsScns: Map[EventId, ScenarioBuilder] = (for {
    (_, scheduleEvent) <- createEventsByOrg.map(tup => tup._1 -> tup._2.map(genScheduleEvent))
  } yield scheduleEvent
    .map(req =>
      req.eventId.getOrElse(EventId("EVENTID NOT FOUND")) ->
        createScn[ScheduleEvent](
          req.eventId.map(_.id).getOrElse("EVENTID NOT FOUND"),
          s"/event/schedule",
          req
        )
    )).flatten.toMap

  val createStoresByEvent: Map[EventId, Seq[CreateStore]] = establishOrgs.flatMap { case (member, org) =>
    val orgId = org.organizationId.getOrElse(OrganizationId("ORGANIZATION NOT FOUND"))
    createEventsByOrg(orgId)
      .flatMap(_.eventId)
      .zip(
        genCreateStores(
          numStoresPerEvent,
          member,
          org,
          createEventsByOrg(orgId)
        )
      )
  }.toMap

  val createStoresScns: Map[EventId, Seq[(StoreId, ScenarioBuilder)]] = (for {
    (eventId, createStores) <- createStoresByEvent
  } yield createStores.map { req =>
    eventId -> (req.storeId.getOrElse(StoreId.defaultInstance) -> createScn[CreateStore](
      req.storeId.map(_.id).getOrElse("STOREID NOT FOUND"),
      s"/store",
      req
    ))
  }).flatten.groupBy(_._1).map(tup => tup._1 -> tup._2.map(_._2).toSeq)

  val readyStoresScns: Map[StoreId, ScenarioBuilder] = (for {
    (_, readyStore) <- createStoresByEvent.map(tup => tup._1 -> tup._2.map(genMakeStoreReady))
  } yield readyStore
    .map(req =>
      req.storeId.getOrElse(StoreId("STOREID NOT FOUND")) ->
        createScn[MakeStoreReady](req.storeId.map(_.id).getOrElse("STOREID NOT FOUND"), s"/store/ready", req)
    )).flatten.toMap

  val createProductsByStore: Map[StoreId, Seq[CreateProduct]] = createStoresByEvent.toSeq.flatMap { createStore =>
    createStore._2
      .map(store =>
        store.storeId.getOrElse(StoreId.defaultInstance) -> genCreateProducts(
          numProductsPerEventStore,
          store.onBehalfOf,
          createEventsByOrg(store.info.flatMap(_.sponsoringOrg).getOrElse(OrganizationId.defaultInstance))
            .groupBy(_.eventId)
            .get(Some(createStore._1))
            .map(_.head)
            .getOrElse(CreateEvent.defaultInstance)
        )
      )
      .toMap
  }.toMap

  val createProductsScns: Map[StoreId, Seq[(Sku, ScenarioBuilder)]] = (for {
    (storeId, createProducts) <- createProductsByStore
  } yield createProducts
    .map { createProduct =>
      storeId -> (createProduct.sku.getOrElse(Sku.defaultInstance) -> createScn[CreateProduct](
        createProduct.sku.map(_.sku).getOrElse("SKU NOT FOUND"),
        s"/product",
        createProduct
      ))
    }).flatten.groupBy(_._1).map(tup => tup._1 -> tup._2.map(_._2).toSeq)

  val activateProductScns: Map[StoreId, Map[Sku, ScenarioBuilder]] = (for {
    (storeId, activateProduct) <- createProductsByStore.map(tup => tup._1 -> tup._2.map(genActivateProduct))
  } yield activateProduct
    .map(req =>
      storeId -> (req.sku.getOrElse(Sku.defaultInstance) ->
        createScn[ActivateProduct](
          req.sku.map(_.sku).getOrElse("SKU NOT FOUND"),
          s"/product/activate",
          req
        ))
    )).flatten.groupBy(_._1).map(tup => tup._1 -> tup._2.map(_._2).toMap)

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
                      .andThen(
                        scheduleEventsScns(createEventOrgIDTup._1)
                          .inject(injectionProfile)
                          .andThen(
                            createStoresScns(createEventOrgIDTup._1).map(storeScnTup =>
                              storeScnTup._2
                                .inject(injectionProfile)
                                .andThen {
                                  readyStoresScns(storeScnTup._1)
                                    .inject(injectionProfile)
                                    .andThen(createProductsScns(storeScnTup._1).map { productScn =>
                                      productScn._2
                                        .inject(injectionProfile)
                                        .andThen(
                                          activateProductScns(storeScnTup._1)(productScn._1).inject(injectionProfile)
                                        )
                                    }: _*)
                                }
                            ): _*
                          )
                      )
                  }.toSeq
                }
              }
            }
          }
      }: _*
  ).protocols(httpProtocol)
}
