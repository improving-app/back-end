package com.improving.app.common.domain

import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat

import scala.language.implicitConversions

object util {

  implicit class AddressUtil(address: Address) {

    implicit def toEditable: EditableAddress = EditableAddress(
      line1 = Some(address.line1),
      line2 = address.line2,
      city = Some(address.city),
      stateProvince = Some(address.stateProvince),
      country = Some(address.country),
      postalCode = address.postalCode
    )

    implicit def updateAddress(editable: EditableAddress): Address = Address(
      line1 = editable.line1.getOrElse(address.line1),
      line2 = editable.line2.orElse(address.line2),
      city = editable.city.getOrElse(address.country),
      stateProvince = editable.stateProvince.getOrElse(address.stateProvince),
      country = editable.country.getOrElse(address.country),
      postalCode = editable.postalCode.orElse(address.postalCode)
    )
  }

  implicit class ContactUtil(contact: Contact) {
    implicit def toEditable: EditableContact = EditableContact(
      firstName = Some(contact.firstName),
      lastName = Some(contact.lastName),
      emailAddress = contact.emailAddress,
      phone = contact.phone,
      userName = Some(contact.userName)
    )

    implicit def updateContact(editable: EditableContact): Contact = Contact(
      firstName = editable.firstName.getOrElse(contact.firstName),
      lastName = editable.lastName.getOrElse(contact.lastName),
      emailAddress = editable.emailAddress.orElse(contact.emailAddress),
      phone = editable.phone.orElse(contact.phone),
      userName = editable.userName.getOrElse(contact.userName)
    )
  }

  implicit class EditableAddressUtil(address: EditableAddress) {
    implicit def copyFromEditable(oldAddress: Address): Address =
      Address(
        line1 = address.line1.getOrElse(oldAddress.line1),
        line2 = address.line2.orElse(oldAddress.line2),
        city = address.city.getOrElse(oldAddress.city),
        stateProvince = address.stateProvince.getOrElse(oldAddress.stateProvince),
        country = address.country.getOrElse(oldAddress.country),
        postalCode = address.postalCode.orElse(oldAddress.postalCode),
      )

    implicit def toAddress: Address = Address(
      line1 = address.getLine1,
      line2 = address.line2,
      city = address.getCity,
      stateProvince = address.getStateProvince,
      country = address.getCountry,
      postalCode = address.postalCode
    )

    implicit def updateAddress(editable: EditableAddress): EditableAddress = EditableAddress(
      line1 = editable.line1.orElse(address.line1),
      line2 = editable.line2.orElse(address.line2),
      city = editable.city.orElse(address.city),
      stateProvince = editable.stateProvince.orElse(address.stateProvince),
      country = editable.country.orElse(address.country),
      postalCode = editable.postalCode.orElse(address.postalCode)
    )
  }

  implicit class EditableContactUtil(contact: EditableContact) {
    implicit def copyFromEditable(oldContact: Contact): Contact = Contact(
      firstName = contact.firstName.getOrElse(oldContact.firstName),
      lastName = contact.lastName.getOrElse(oldContact.lastName),
      emailAddress = contact.emailAddress.orElse(oldContact.emailAddress),
      phone = contact.phone.orElse(oldContact.phone),
      userName = contact.userName.getOrElse(oldContact.userName),
    )

    implicit def toContact: Contact = Contact(
      firstName = contact.getFirstName,
      lastName = contact.getLastName,
      emailAddress = contact.emailAddress,
      phone = contact.phone,
      userName = contact.getUserName
    )

    implicit def updateContact(editable: EditableContact): EditableContact = EditableContact(
      firstName = editable.firstName.orElse(contact.firstName),
      lastName = editable.lastName.orElse(contact.lastName),
      emailAddress = editable.emailAddress.orElse(contact.emailAddress),
      phone = editable.phone.orElse(contact.phone),
      userName = editable.userName.orElse(contact.userName)
    )
  }

  implicit class GeneratedMessageUtil[T <: GeneratedMessage](req: T) {
    implicit def print: String = s"""\"${JsonFormat.toJsonString(req).replace("\"", "\\\"")}\""""
  }

}
