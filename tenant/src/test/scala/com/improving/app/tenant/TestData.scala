package com.improving.app.tenant

import com.improving.app.common.domain.{
  Address,
  CaPostalCodeImpl,
  Contact,
  EditableAddress,
  EditableContact,
  PostalCodeMessageImpl
}
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

  val baseTenantInfo: TenantInfo = infoFromEditableInfo(
    EditableTenantInfo(
      name = Some("Tenant Name"),
      primaryContact = Some(baseContact),
      address = Some(baseAddress),
      organizations = Some(TenantOrganizationList())
    )
  )
}
