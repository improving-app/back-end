package com.improving.app.gateway.domain.common

import com.improving.app.gateway.domain.common.PostalCode.PostalCode

import java.util.UUID

object IdTypes {
  type TenantId = UUID
  type OrganizationId = UUID
  type MemberId = UUID
}

object PostalCode extends Enumeration {
  type PostalCode = Value
  val CA, US = Value
}

case class Address(
    line1: String,
    line2: String,
    city: String,
    stateProvince: String,
    country: String,
    postalCode: PostalCode
)

case class Contact(
    firstName: String,
    lastName: String,
    emailAddress: Option[String],
    phone: Option[String],
    userName: String
)
