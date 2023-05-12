package com.improving.app.tenant

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, EditableAddress, EditableContact, PostalCodeMessageImpl}
import com.improving.app.common.service.util.{doForSameIfHas, doIfHas}
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

  def infoFromEditableInfo(infoToUpdate: EditableTenantInfo, infoToCopy: TenantInfo): TenantInfo =
    infoToCopy
      .copy(
        name = doForSameIfHas[String](infoToUpdate.name, infoToCopy.name),
        address = doIfHas[EditableAddress, Address](
          infoToUpdate.address,
          infoToCopy.address,
          v => addressFromEditableAddress(v, infoToCopy.address)

        ),
        primaryContact = doIfHas[EditableContact, Contact](
          infoToUpdate.primaryContact,
          infoToCopy.primaryContact,
          v => contactFromEditableContact(v, infoToCopy.primaryContact)

        ),
        organizations =
          if (infoToUpdate.organizations.isDefined) infoToUpdate.getOrganizations else infoToCopy.organizations
      )

  def addressFromEditableAddress(addressToUpdate: EditableAddress, addressToCopy: Address): Address = addressToCopy.copy(
    line1 = doForSameIfHas[String](addressToUpdate.line1, addressToCopy.line1),
    line2 = Some(doForSameIfHas[String](addressToUpdate.line2, addressToCopy.getLine2)),
    city = doForSameIfHas[String](addressToUpdate.city, addressToCopy.city),
    stateProvince = doForSameIfHas[String](addressToUpdate.stateProvince, addressToCopy.stateProvince),
    country = doForSameIfHas[String](addressToUpdate.country, addressToCopy.country),
    postalCode = Some(doForSameIfHas[PostalCodeMessageImpl](addressToUpdate.postalCode, addressToCopy.getPostalCode))
  )

  def contactFromEditableContact(contactToUpdate: EditableContact, contactToCopy: Contact): Contact = Contact(
    firstName = doForSameIfHas[String](contactToUpdate.firstName, contactToCopy.firstName),
    lastName = doForSameIfHas[String](contactToUpdate.lastName, contactToCopy.lastName),
    emailAddress =
      Some(doForSameIfHas[String](contactToUpdate.emailAddress, contactToCopy.getEmailAddress)),
    phone = Some(doForSameIfHas[String](contactToUpdate.phone, contactToCopy.getPhone)),
    userName = doForSameIfHas[String](contactToUpdate.userName, contactToCopy.userName),
  )

  val baseTenantInfo: TenantInfo = TenantInfo(
    name = "Tenant Name",
    primaryContact = baseContact,
    address = baseAddress,
    organizations = TenantOrganizationList()
  )
}
