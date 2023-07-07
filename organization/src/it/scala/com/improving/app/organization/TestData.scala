package com.improving.app.organization

import com.improving.app.common.domain.util.{AddressUtil, EditableAddressUtil}
import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, PostalCodeMessageImpl, TenantId}
import com.improving.app.organization.domain.{EditableOrganizationInfo, OrganizationInfo}

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

  val baseOrganizationInfo: EditableOrganizationInfo = EditableOrganizationInfo(
    name = Some("Organization Name"),
    shortName = Some("OrgName"),
    tenant = Some(TenantId("tenant")),
    address = Some(baseAddress.toEditable),
  )

  def organizationInfoFromEditableInfo(editableInfo: EditableOrganizationInfo): OrganizationInfo = OrganizationInfo(
    name = editableInfo.getName,
    shortName = editableInfo.shortName,
    isPublic = editableInfo.getIsPublic,
    address = editableInfo.address.map(_.toAddress),
    tenant = editableInfo.tenant,
    url = editableInfo.url,
    logo = editableInfo.logo
  )
}
