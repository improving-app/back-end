package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain.{EventId, MemberId, StoreId}
import com.improving.app.gateway.domain.event.CreateEvent
import com.improving.app.gateway.domain.store.{CreateStore, EditableStoreInfo, MakeStoreReady}
import com.improving.app.gateway.domain.organization.EstablishOrganization
import com.improving.app.gatling.common.gen._

import java.util.UUID
import scala.util.Random

object storeGen {
  def genCreateStores(
      numStoresPerEvent: Int,
      creatingMember: Option[MemberId],
      establishOrg: EstablishOrganization,
      eventsForOrg: Seq[CreateEvent]
  ): Seq[Seq[CreateStore]] = eventsForOrg.map { event =>
    (0 until numStoresPerEvent)
      .map(_ => StoreId(UUID.randomUUID().toString))
      .zip(
        (0 until numStoresPerEvent).map(_ => creatingMember)
      )
      .map { case (id, creatingMember) =>
        val orgName: String = establishOrg.organizationInfo.flatMap(_.name).getOrElse("ORG NAME NOT FOUND")
        val eventName: String = event.info.flatMap(_.eventName).getOrElse("EVENT NAME NOT FOUND")
        CreateStore(
          Some(id),
          creatingMember,
          Some(
            EditableStoreInfo(
              name = Some(s"$eventName Event Store"),
              description = Some(s"This store sells items the event $eventName for organization $orgName"),
              products = Seq(),
              event = event.eventId,
              sponsoringOrg = establishOrg.organizationId
            )
          )
        )
      }
  }

  def genMakeStoreReady(createStore: CreateStore): MakeStoreReady =
    MakeStoreReady(createStore.storeId, createStore.onBehalfOf)
}
