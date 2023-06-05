package com.improving.app.organization

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, PostalCodeMessageImpl, TenantId}
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

  val baseContact: Contact = Contact(
    firstName = "Bob",
    lastName = "Dobbs",
    emailAddress = Some("bob@dobbs.com"),
    userName = "bobdobbs23",
    phone = Some("555-2323"),
  )

  val baseOrganizationInfo: OrganizationInfo = OrganizationInfo(
    name = "Organization Name",
    shortName = Some("OrgName"),
    tenant = TenantId("tenant"),
    address = Some(baseAddress),
  )
}
