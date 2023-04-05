package com.improving.app.tenant

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, PostalCodeMessageImpl}
import com.improving.app.tenant.domain.{TenantInfo, TenantOrganizationList}

object TestData {
  val baseContact = Contact(
    firstName = "firstName",
    lastName = "lastName",
    emailAddress = Some("test@test.com"),
    phone = Some("111-111-1111"),
    userName = "contactUsername"
  )
  val baseAddress = Address(
    line1 = "line1",
    line2 = "line2",
    city = "city",
    stateProvince = "stateProvince",
    country = "country",
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  val baseTenantInfo = TenantInfo(
    name = "Tenant Name",
    primaryContact = Some(baseContact),
    address = Some(baseAddress),
    organizations = Some(TenantOrganizationList())
  )
}
