package com.improving.app.common.domain

import com.improving.app.common.service.util.doForSameIfHas

object util {
  def addressFromEditableAddress(addressToUpdate: EditableAddress, addressToCopy: Address): Address =
    addressToCopy.copy(
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
    emailAddress = Some(doForSameIfHas[String](contactToUpdate.emailAddress, contactToCopy.getEmailAddress)),
    phone = Some(doForSameIfHas[String](contactToUpdate.phone, contactToCopy.getPhone)),
    userName = doForSameIfHas[String](contactToUpdate.userName, contactToCopy.userName),
  )
}
