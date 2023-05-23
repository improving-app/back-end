package com.improving.app.tenant

import com.improving.app.common.domain.{CaPostalCodeImpl, EditableAddress, EditableContact, PostalCodeMessageImpl}
import com.improving.app.tenant.domain.{EditableTenantInfo, TenantOrganizationList}

object TestData {
  val itBaseContact: EditableContact = EditableContact(
    firstName = Some("firstName"),
    lastName = Some("lastName"),
    emailAddress = Some("test@test.com"),
    phone = Some("111-111-1111"),
    userName = Some("contactUsername")
  )
  val itBaseAddress: EditableAddress = EditableAddress(
    line1 = Some("line1"),
    line2 = Some("line2"),
    city = Some("city"),
    stateProvince = Some("stateProvince"),
    country = Some("country"),
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  val itBaseTenantInfo: EditableTenantInfo = EditableTenantInfo(
    name = Some("Tenant Name"),
    primaryContact = Some(itBaseContact),
    address = Some(itBaseAddress),
    organizations = Some(TenantOrganizationList())
  )
}
