package com.improving.app.store.domain

import com.improving.app.common.domain.OrganizationId

object TestData {
  val baseStoreInfo: StoreOrEditableInfo = StoreOrEditableInfo(
    StoreOrEditableInfo.InfoOrEditable.Info(
      StoreInfo(
        name = "Store Name",
        description = "Here is the description.",
        sponsoringOrg = Some(OrganizationId("Sponsor"))
      )
    )
  )
}
