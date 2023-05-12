package com.improving.app.tenant

import com.improving.app.common.domain.util.{addressFromEditableAddress, contactFromEditableContact}
import com.improving.app.common.domain.{
  Address,
  CaPostalCodeImpl,
  Contact,
  EditableAddress,
  EditableContact,
  PostalCodeMessageImpl
}
import com.improving.app.common.service.util.{doForSameIfHas, doIfHas}
import com.improving.app.common.test.domain.util.{testAddressFromEditableAddress, testContactFromEditableContact}
import com.improving.app.tenant.domain.{EditableTenantInfo, TenantInfo, TenantOrganizationList}

object TestData {
  val baseContact: Contact = Contact(
    firstName = "firstName",
    lastName = "lastName",
    emailAddress = Some("test@test.com"),
    phone = Some("111-111-1111"),
    userName = "contactUsername"
  )
  val baseAddress: Address = Address(
    line1 = "line1",
    line2 = Some("line2"),
    city = "city",
    stateProvince = "stateProvince",
    country = "country",
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  def infoFromEditableInfo(editable: EditableTenantInfo): TenantInfo = TenantInfo(
    name = editable.getName,
    primaryContact = testContactFromEditableContact(editable.getPrimaryContact),
    address = testAddressFromEditableAddress(editable.getAddress),
    organizations = editable.getOrganizations
  )

  val baseTenantInfo: TenantInfo = TenantInfo(
    name = "Tenant Name",
    primaryContact = baseContact,
    address = baseAddress,
    organizations = TenantOrganizationList()
  )
}
