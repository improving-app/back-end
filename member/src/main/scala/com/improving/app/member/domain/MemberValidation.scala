package com.improving.app.member.domain

import cats.data.ValidatedNec
import cats.implicits.{catsSyntaxTuple5Semigroupal, catsSyntaxTuple9Semigroupal, catsSyntaxValidatedIdBinCompat0}
import com.improving.app.common.domain.{Contact, OrganizationId, TenantId}

object MemberValidation {
  type ValidationResult[A] = ValidatedNec[MemberValidationError, A]

  sealed trait MemberValidationError {
    def errorMessage: String
  }

  case class FirstNameHasSpecialCharacters(firstName: String) extends MemberValidationError {
    val errorMessage: String = "First name cannot contain spaces, numbers or special characters."
  }

  case class LastNameHasSpecialCharacters(lastName: String) extends MemberValidationError {
    val errorMessage: String = "Last name cannot contain spaces, numbers or special characters."
  }

  case class MissingOrInvalidPhoneNumber(phoneNumber: Option[String]) extends MemberValidationError {
    val errorMessage: String = s"Missing or invalid phone number ${phoneNumber.getOrElse("")}."
  }

  case class MissingOrInvalidEmailAddress(emailAddress: Option[String]) extends MemberValidationError {
    val errorMessage: String = s"Missing or invalid email address ${emailAddress.getOrElse("")}."
  }

  case class NoOrInvalidOrganizationsAssociated(organizations: Seq[OrganizationId]) extends MemberValidationError {
    val errorMessage: String = s"No or Invalid organizations associated : ${organizations.mkString(",")}"
  }

  case class NoOrInvalidTenantAssociated(tenant: Option[TenantId]) extends MemberValidationError {
    val errorMessage: String = s"No or Invalid tenant associated : ${tenant.mkString(",")}"
  }

  case object NoContactAssociated extends MemberValidationError {
    val errorMessage: String = s"No contact information associated"
  }

  private def validateFirstName(firstName: String): ValidationResult[String] =
    if (firstName.matches("^[a-zA-Z]+$")) firstName.validNec else FirstNameHasSpecialCharacters(firstName).invalidNec

  private def validateLastName(lastName: String): ValidationResult[String] =
    if (lastName.matches("^[a-zA-Z]+$")) lastName.validNec else LastNameHasSpecialCharacters(lastName).invalidNec

  //TODO validate organizations against organization service
  private def validateOrganizations(memberInfo: MemberInfo): ValidationResult[Seq[OrganizationId]] =
    if (memberInfo.organizations.nonEmpty) memberInfo.organizations.validNec
    else NoOrInvalidOrganizationsAssociated(memberInfo.organizations).invalidNec

  //TODO validate tenant against tenant service
  private def validateTenant(memberInfo: MemberInfo): ValidationResult[Option[TenantId]] =
    if (memberInfo.tenant.nonEmpty) memberInfo.tenant.validNec
    else NoOrInvalidTenantAssociated(memberInfo.tenant).invalidNec

  //TODO validate phone number against regex
  private def validatePhoneNo(phone: Option[String]): ValidationResult[Option[String]] =
    if (phone.nonEmpty) phone.validNec
    else MissingOrInvalidPhoneNumber(phone).invalidNec

  private def validateEmailAddress(emailAddress: Option[String]): ValidationResult[Option[String]] =
    if (emailAddress.nonEmpty) emailAddress.validNec
    else MissingOrInvalidEmailAddress(emailAddress).invalidNec

  private def validateContact(memberInfo: MemberInfo): ValidationResult[Option[Contact]] =
    if (memberInfo.contact.isEmpty) NoContactAssociated.invalidNec
    else {
      (
        validateFirstName(memberInfo.contact.get.firstName),
        validateLastName(memberInfo.contact.get.lastName),
        if (memberInfo.notificationPreference == NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL)
          validateEmailAddress(memberInfo.contact.get.emailAddress)
        else memberInfo.contact.get.emailAddress.validNec,
        if (memberInfo.notificationPreference == NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
          validatePhoneNo(memberInfo.contact.get.phone)
        else memberInfo.contact.get.phone.validNec,
        memberInfo.contact.get.userName.validNec
      ).mapN(Contact.apply).map(Some(_))

    }

  def validateMemberInfo(
      memberInfo: MemberInfo
  ): ValidationResult[MemberInfo] =
    (
      memberInfo.handle.validNec,
      memberInfo.avatarUrl.validNec,
      validateFirstName(memberInfo.firstName),
      validateLastName(memberInfo.lastName),
      memberInfo.notificationPreference.validNec,
      memberInfo.notificationOptIn.validNec,
      validateContact(memberInfo),
      validateOrganizations(memberInfo),
      validateTenant(memberInfo)
    ).mapN(MemberInfo.apply)

}
