package com.improving.app.store.domain

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, OrganizationId, PostalCodeMessageImpl, TenantId}

import java.util.UUID

object TestData {
  val baseStoreInfo: StoreInfo = StoreInfo(
    name = "Store Name",
    description = "Here is the description.",
    sponsoringOrg = Some(OrganizationId("Sponsor"))
  )
}
