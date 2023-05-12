package com.improving.app.tenant

import com.improving.app.common.domain.{CaPostalCodeImpl, EditableAddress, EditableContact, PostalCodeMessageImpl}
import com.improving.app.common.test.domain.util.{testAddressFromEditableAddress, testContactFromEditableContact}
import com.improving.app.tenant.domain.{EditableTenantInfo, TenantInfo, TenantOrganizationList}

object TestData {
  val baseContact: EditableContact = EditableContact(
    firstName = Some("firstName"),
    lastName = Some("lastName"),
    emailAddress = Some("test@test.com"),
    phone = Some("111-111-1111"),
    userName = Some("contactUsername")
  )
  val baseAddress: EditableAddress = EditableAddress(
    line1 = Some("line1"),
    line2 = Some("line2"),
    city = Some("city"),
    stateProvince = Some("stateProvince"),
    country = Some("country"),
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  def infoFromEditableInfo(editable: EditableTenantInfo): TenantInfo = TenantInfo(
    name = editable.getName,
    primaryContact = testContactFromEditableContact(editable.getPrimaryContact),
    address = testAddressFromEditableAddress(editable.getAddress),
    organizations = editable.getOrganizations
  )

  val baseTenantInfo: TenantInfo = infoFromEditableInfo(
    EditableTenantInfo(
      name = Some("Tenant Name"),
      primaryContact = Some(baseContact),
      address = Some(baseAddress),
      organizations = Some(TenantOrganizationList())
    )
  )
}
