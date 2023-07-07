package com.improving.app.common.errors

import com.improving.app.common.domain.{
  CaPostalCodeImpl,
  Contact,
  EditableAddress,
  MemberId,
  OrganizationId,
  StoreId,
  TenantId,
  UsPostalCodeImpl
}

object Validation {

  type Validator[T] = T => Option[ValidationError]

  def applyAllValidators[T](validators: Validator[T]*): Validator[T] =
    (validatee: T) =>
      validators.foldLeft[Option[ValidationError]](None)((maybeAlreadyError: Option[ValidationError], validator) =>
        maybeAlreadyError.orElse(validator(validatee))
      )

  def requiredThenValidate[T]: (String, Validator[T]) => Validator[Option[T]] = (fieldName, validator) => { opt =>
    if (opt.isEmpty) {
      Some(ValidationError("No associated " + fieldName))
    } else {
      validator(opt.get)
    }
  }

  def required[T]: String => Validator[Option[T]] = fieldName => { opt =>
    if (opt.isEmpty) {
      Some(ValidationError("No associated " + fieldName))
    } else {
      None
    }
  }

  def listHasLength[T]: String => Validator[Seq[T]] = fieldName => { list =>
    if (list.isEmpty) {
      Some(ValidationError("List " + fieldName + "has no length"))
    } else {
      None
    }
  }

  def nonEmpty: String => Validator[Seq[_]] = fieldName => { seq =>
    if (seq.isEmpty) {
      Some(ValidationError("Empty " + fieldName))
    } else {
      None
    }
  }

  def optional[T]: Validator[T] => Validator[Option[T]] = validator => opt => opt.flatMap(validator)

  def skipEmpty(validator: Validation.Validator[String]): Validator[String] = str => {
    if (str.isEmpty) {
      None
    } else {
      validator(str)
    }
  }

  def validateAll[T](validator: Validator[T]): Validator[Iterable[T]] = iterable => {
    iterable.foldLeft[Option[ValidationError]](None)(
      (maybeExistingError: Option[ValidationError], elementToValidate: T) => {
        if (maybeExistingError.isDefined) {
          maybeExistingError
        } else {
          validator(elementToValidate)
        }
      }
    )
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

  val storeIdValidator: Validator[StoreId] = storeId => {
    if (storeId.id.isEmpty) {
      Some(ValidationError("Store Id is empty"))
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

  val editableAddressValidator: Validator[EditableAddress] =
    applyAllValidators[EditableAddress](
      address => required("line1")(address.line1),
      address => required("city")(address.city),
      address => required("stateProvince")(address.stateProvince),
      address => required("country")(address.country),
      address => required("postalCode")(address.postalCode)
    )
}
