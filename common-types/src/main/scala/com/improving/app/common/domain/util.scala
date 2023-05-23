package com.improving.app.common.domain

object util {
  def copyAddressFromEditableAddress(newAddress: EditableAddress, oldAddress: Address): Address =
    Address(
      line1 = newAddress.line1.getOrElse(oldAddress.line1),
      line2 = newAddress.line2.orElse(oldAddress.line2),
      city = newAddress.city.getOrElse(oldAddress.city),
      stateProvince = newAddress.stateProvince.getOrElse(oldAddress.stateProvince),
      country = newAddress.country.getOrElse(oldAddress.country),
      postalCode = newAddress.postalCode.orElse(oldAddress.postalCode),
    )

  def copyContactFromEditableContact(newContact: EditableContact, oldContact: Contact): Contact = Contact(
    firstName = newContact.firstName.getOrElse(oldContact.firstName),
    lastName = newContact.lastName.getOrElse(oldContact.lastName),
    emailAddress = newContact.emailAddress.orElse(oldContact.emailAddress),
    phone = newContact.phone.orElse(oldContact.phone),
    userName = newContact.userName.getOrElse(oldContact.userName),
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

}
