package com.improving.app.common.domain

import scala.language.implicitConversions

object util {
  implicit class AddressUtil(newAddress: EditableAddress) {
    implicit def copyFromEditable(oldAddress: Address): Address =
      Address(
        line1 = newAddress.line1.getOrElse(oldAddress.line1),
        line2 = newAddress.line2.orElse(oldAddress.line2),
        city = newAddress.city.getOrElse(oldAddress.city),
        stateProvince = newAddress.stateProvince.getOrElse(oldAddress.stateProvince),
        country = newAddress.country.getOrElse(oldAddress.country),
        postalCode = newAddress.postalCode.orElse(oldAddress.postalCode),
      )

    implicit def toAddress: Address = Address(
      line1 = newAddress.getLine1,
      line2 = newAddress.line2,
      city = newAddress.getCity,
      stateProvince = newAddress.getStateProvince,
      country = newAddress.getCountry,
      postalCode = newAddress.postalCode
    )
  }

  implicit class ContactUtil(newContact: EditableContact) {
    implicit def copyFromEditable(oldContact: Contact): Contact = Contact(
      firstName = newContact.firstName.getOrElse(oldContact.firstName),
      lastName = newContact.lastName.getOrElse(oldContact.lastName),
      emailAddress = newContact.emailAddress.orElse(oldContact.emailAddress),
      phone = newContact.phone.orElse(oldContact.phone),
      userName = newContact.userName.getOrElse(oldContact.userName),
    )

    implicit def toContact: Contact = Contact(
      firstName = newContact.getFirstName,
      lastName = newContact.getLastName,
      emailAddress = newContact.emailAddress,
      phone = newContact.phone,
      userName = newContact.getUserName
    )
  }

}
