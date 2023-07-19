package com.improving.app.store.domain

import com.improving.app.common.domain.{EventId, OrganizationId, Sku}

import java.util.UUID

object TestData {
  val baseStoreInfo: StoreInfo = StoreInfo(
    name = "Store Name",
    description = "Here is the description.",
    products = Seq(Sku(UUID.randomUUID().toString)),
    event = Some(EventId(UUID.randomUUID().toString)),
    sponsoringOrg = Some(OrganizationId("Sponsor"))
  )
}
