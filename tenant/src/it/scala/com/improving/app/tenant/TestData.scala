package com.improving.app.tenant

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, PostalCodeMessageImpl}
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
    line2 = "line2",
    city = "city",
    stateProvince = "stateProvince",
    country = "country",
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )

  def infoFromEditableInfo(editable: EditableTenantInfo): TenantInfo = TenantInfo(
    name = editable.getName,
    primaryContact = Contact(
      firstName = editable.getPrimaryContact.getFirstName,
      lastName = editable.getPrimaryContact.getLastName,
      emailAddress = editable.getPrimaryContact.emailAddress,
      phone = editable.getPrimaryContact.phone,
      userName = editable.getPrimaryContact.getUserName
    ),
    address = Address(
      line1 = editable.getAddress.getLine1,
      line2 = editable.getAddress.getLine2,
      city = editable.getAddress.getCity,
      stateProvince = editable.getAddress.getStateProvince,
      country = editable.getAddress.getCountry,
      postalCode = editable.getAddress.postalCode
    ),
    organizations = editable.getOrganizations
  )

  val baseTenantInfo: TenantInfo = TenantInfo(
    name = "Tenant Name",
    primaryContact = baseContact,
    address = baseAddress,
    organizations = TenantOrganizationList()
  )
}
