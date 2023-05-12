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
    primaryContact = contactFromEditableContact(editable.getPrimaryContact),
    address = addressFromEditableAddress(editable.getAddress),
    organizations = editable.getOrganizations
  )

  def addressFromEditableAddress(editable: EditableAddress): Address = Address(
    line1 = editable.getLine1,
    line2 = editable.line2,
    city = editable.getCity,
    stateProvince = editable.getStateProvince,
    country = editable.getCountry,
    postalCode = editable.postalCode
  )
  def contactFromEditableContact(editable: EditableContact): Contact = Contact(
    firstName = editable.getFirstName,
    lastName = editable.getLastName,
    emailAddress = editable.emailAddress,
    phone = editable.phone,
    userName = editable.getUserName
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
