package com.improving.app.organization.domain

import cats.data.ValidatedNec
import cats.implicits._
import com.improving.app.organization._

/** Organization(
  *    orgId
  *    info
  *    parent
  *    members
  *    owners
  *    contacts
  *    meta
  *    status
  *    )
  */
object OrganizationValidation {
  type ValidationResult[A] = ValidatedNec[OrganizationValidationError, A]

  sealed trait OrganizationValidationError {
    def errorMessage: String
  }

  case class StringIsEmptyError(field: String)
      extends OrganizationValidationError {
    val errorMessage: String =
      s"$field cannot be empty string."
  }

  case class ValueIsNoneError[T](value: Option[T])
      extends OrganizationValidationError {
    val errorMessage: String =
      s"value cannot be None."
  }

  private def validateNonEmptyString(
      field: String,
      value: String
  ): ValidationResult[String] =
    if (!value.isEmpty) value.validNec
    else StringIsEmptyError(field).invalidNec

  def validateInfo(
      info: Info
  ): ValidationResult[Info] =
    (
      validateNonEmptyString("name", info.name),
      info.shortName.validNec,
      info.address.validNec,
      info.isPrivate.validNec,
      info.url.validNec,
      info.logo.validNec,
      info.tenant.validNec
    ).mapN((name, shortName, address, isPrivate, url, logo, tenant) =>
      Info(name, shortName, address, isPrivate, url, logo, tenant)
    )
}
