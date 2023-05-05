package com.improving.app.common.errors

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, MemberId, OrganizationId, TenantId, UsPostalCodeImpl}

object Validation {

  type Validator[T] = T => Option[ValidationError]

  def applyAllValidators[T](validators: Validator[T]*): Validator[T] =
    (validatee: T) =>
      validators.foldLeft[Option[ValidationError]](None)(
        (maybeAlreadyError: Option[ValidationError], validator) =>
          maybeAlreadyError.orElse(validator(validatee))
      )

  def required[T]: (String, Validator[T]) => Validator[Option[T]] = (fieldName, validator) => {
    opt =>
      if (opt.isEmpty) {
        Some(ValidationError("No associated " + fieldName))
      } else {
        validator(opt.get)
      }
  }

  def optional[T]: Validator[T] => Validator[Option[T]] = validator => opt => opt.flatMap(validator)

  def skipEmpty(validator: Validation.Validator[String]): Validator[String] = str => {
    if(str.isEmpty) {
      None
    } else {
      validator(str)
    }
  }

  val urlValidator: Validator[String] = url => {
    None // TODO
  }

  val tenantIdValidator: Validator[TenantId] = tenantId => {
    if (tenantId.id.isEmpty) {
      Some(ValidationError("Tenant Id is empty"))
    } else {
      None
    }
  }

  val memberIdValidator: Validator[MemberId] = memberId => {
    if (memberId.id.isEmpty) {
      Some(ValidationError("Member Id is empty"))
    } else {
      None
    }
  }

  val organizationIdValidator: Validator[OrganizationId] = organizationId => {
    if (organizationId.id.isEmpty) {
      Some(ValidationError("Organization Id is empty"))
    } else {
      None
    }
  }

  val contactValidator: Validator[Contact] = contact => {
    if (
      contact.firstName.isEmpty ||
        contact.lastName.isEmpty ||
        contact.emailAddress.forall(_.isEmpty) ||
        contact.phone.forall(_.isEmpty) ||
        contact.userName.isEmpty
    ) {
      Some(ValidationError("Primary contact info is not complete"))
    } else {
      None
    }
  }

  val addressValidator: Validator[Address] = address => {
    val isPostalCodeMissing = address.postalCode.fold(true) {
      postalCode =>
        postalCode.postalCodeValue match {
          case UsPostalCodeImpl(code) => code.isEmpty
          case CaPostalCodeImpl(code) => code.isEmpty
        }
    }
    if (
      address.line1.isEmpty ||
        address.city.isEmpty ||
        address.stateProvince.isEmpty ||
        address.country.isEmpty ||
        isPostalCodeMissing
    ) {
      Some(ValidationError("Address information is not complete"))
    } else {
      None
    }
  }
}
