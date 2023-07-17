package com.improving.app.store

import com.improving.app.common.domain.{EventId, OrganizationId, Sku}
import com.improving.app.store.domain.{StoreInfo, StoreOrEditableInfo}

import java.util.UUID

object TestData {
  val baseStoreInfo: StoreOrEditableInfo = StoreOrEditableInfo(
    StoreOrEditableInfo.InfoOrEditable.Info(
      StoreInfo(
        name = "Store Name",
        description = "Here is the description.",
        products = Seq(Sku(UUID.randomUUID().toString)),
        event = Some(EventId(UUID.randomUUID().toString)),
        sponsoringOrg = Some(OrganizationId("Sponsor"))
      )
    )
  )
}
