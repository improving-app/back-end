package com.improving.app.organization

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, PostalCodeMessageImpl, TenantId}
import com.improving.app.organization.domain.OrganizationInfo

object TestData {
  val baseAddress: Address = Address(
    line1 = "line1",
    line2 = Some("line2"),
    city = "city",
    stateProvince = "stateProvince",
    country = "country",
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  val baseOrganizationInfo: OrganizationInfo = OrganizationInfo(
    name = "Organization Name",
    shortName = "OrgName",
    tenant = Some(TenantId("tenant")),
    address = Some(baseAddress),
  )
}
