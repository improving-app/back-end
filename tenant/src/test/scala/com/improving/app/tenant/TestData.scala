package com.improving.app.tenant

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, PostalCodeMessageImpl}

object TestData {
  val baseContact = Contact(
    firstName = "firstName",
    lastName = "lastName",
    emailAddress = Some("test@test.com"),
    phone = Some("111-111-1111"),
    userName = "contactUsername"
  )
  val baseAddress = Address(
    line1 = "line1",
    line2 = "line2",
    city = "city",
    stateProvince = "stateProvince",
    country = "country",
    postalCode = Some(PostalCodeMessageImpl(CaPostalCodeImpl("caPostalCode")))
  )
}
