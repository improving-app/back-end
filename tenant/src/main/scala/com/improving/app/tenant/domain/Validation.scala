package com.improving.app.tenant.domain

import com.improving.app.common.domain.{Address, CaPostalCodeImpl, Contact, MemberId, OrganizationId, TenantId, UsPostalCodeImpl}

object Validation {
  case class ValidationError(message: String)

  type Validator[T] = T => Option[ValidationError]

  def applyAllValidators[T](validators: Seq[Validator[T]]): Validator[T] =
    (validatee: T) =>
      validators.foldLeft[Option[ValidationError]](None)(
        (maybeAlreadyError: Option[ValidationError], validator) =>
          maybeAlreadyError.orElse(validator(validatee))
      )

  def required[T]: (String, Validator[T]) => Validator[Option[T]] = (fieldName, validator) => {
    opt =>
      if (opt.isEmpty) {
        Some(ValidationError(fieldName + " is missing."))
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

  val organizationsValidator: Validator[Seq[OrganizationId]] = orgs => {
    None
  }

  val tenantNameValidator: Validator[String] = name => {
    if(name.isEmpty) {
      Some(ValidationError("name empty"))
    } else {
      None
    }
  }

  val completeTenantInfoValidator: Validator[TenantInfo] = tenantInfo => {
    applyAllValidators[TenantInfo](Seq(
      ti => tenantNameValidator(ti.name),
      ti => required("primary contact", contactValidator)(ti.primaryContact),
      ti => required("address", addressValidator)(ti.address),
      ti => organizationsValidator(ti.orgs)
    ))(tenantInfo)
  }

  val partialTenantInfoValidator: Validator[TenantInfo] = tenantInfo => {
    applyAllValidators[TenantInfo](Seq(
      ti => skipEmpty(tenantNameValidator)(ti.name),
      ti => optional(contactValidator)(ti.primaryContact),
      ti => optional(addressValidator)(ti.address),
      ti => organizationsValidator(ti.orgs)
    ))(tenantInfo)
  }

}
