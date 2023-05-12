package com.improving.app.common.test.domain

import com.improving.app.common.domain.{Address, Contact, EditableAddress, EditableContact}

object util {
  def testAddressFromEditableAddress(editable: EditableAddress): Address = Address(
    line1 = editable.getLine1,
    line2 = editable.line2,
    city = editable.getCity,
    stateProvince = editable.getStateProvince,
    country = editable.getCountry,
    postalCode = editable.postalCode
  )

  def testContactFromEditableContact(editable: EditableContact): Contact = Contact(
    firstName = editable.getFirstName,
    lastName = editable.getLastName,
    emailAddress = editable.emailAddress,
    phone = editable.phone,
    userName = editable.getUserName
  )

}
