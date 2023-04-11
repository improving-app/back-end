package com.improving.app.member.domain

import cats.data.ValidatedNec
import cats.implicits.{catsSyntaxTuple10Semigroupal, catsSyntaxTuple2Semigroupal, catsSyntaxTuple3Semigroupal, catsSyntaxTuple4Semigroupal, catsSyntaxTuple6Semigroupal, catsSyntaxTuple9Semigroupal, catsSyntaxValidatedIdBinCompat0}
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}

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

  case class NoOrInvalidTenantAssociated(tenant: Option[TenantId]) extends MemberValidationError {
    val errorMessage: String = s"No or Invalid tenant associated : ${tenant.mkString(",")}"
  }

  case object TenantIdIsEmpty extends MemberValidationError {
    val errorMessage: String = s"Tenant Id is empty"
  }

  case object NoMemberIdAssociated extends MemberValidationError {
    val errorMessage: String = s"No member ID associated"
  }

  case object MemberIdIsEmpty extends MemberValidationError {
    val errorMessage: String = s"Member ID is empty"
  }

  case object NoMemberInfoAssociated extends MemberValidationError  {
    val errorMessage: String = s"No member info associated"
  }

  case object NoEditableInfoAssociated extends MemberValidationError {
    val errorMessage: String = s"No editable info associated"
  }

  case object NoContactAssociated extends MemberValidationError {
    val errorMessage: String = s"No contact information associated"
  }

  case object FirstNameIsEmpty extends MemberValidationError {
    val errorMessage: String = s"First name is empty"
  }

  case object LastNameIsEmpty extends MemberValidationError {
    val errorMessage: String = s"Last name is empty"
  }

  case object EmptyOrganizationIdFound extends MemberValidationError  {
    val errorMessage: String = s"Empty organization id found"
  }

  private def validateMemberIdOpt(
    memberIdOpt: Option[MemberId]
  ): ValidationResult[Option[MemberId]] =
    memberIdOpt.fold[ValidationResult[Option[MemberId]]](NoMemberIdAssociated.invalidNec) {
      memberId =>
        if (memberId.id.isEmpty) {
          MemberIdIsEmpty.invalidNec
        } else {
          memberIdOpt.validNec
        }
    }

  private def validateFirstName(
    name: String
  ): ValidationResult[String] = {
    if (name.isEmpty) {
      FirstNameIsEmpty.invalidNec
    } else if (name.matches("^[a-zA-Z]+$")) {
      name.validNec
    } else {
      FirstNameHasSpecialCharacters(name).invalidNec
    }
  }

  private def validateFirstNameOpt(
    nameOpt: Option[String]
  ): ValidationResult[Option[String]] = {
    nameOpt.fold[ValidationResult[Option[String]]](nameOpt.validNec) {
      name => validateFirstName(name).map(Some(_))
    }
  }

  private def validateLastName(
    name: String
  ): ValidationResult[String] = {
    if (name.isEmpty) {
      LastNameIsEmpty.invalidNec
    } else if (name.matches("^[a-zA-Z]+$")) {
      name.validNec
    } else {
      LastNameHasSpecialCharacters(name).invalidNec
    }
  }

  private def validateLastNameOpt(
    nameOpt: Option[String]
  ): ValidationResult[Option[String]] = {
    nameOpt.fold[ValidationResult[Option[String]]](nameOpt.validNec) {
      name => validateLastName(name).map(Some(_))
    }
  }

  private def validateRequiredTenant(
    memberInfo: MemberInfo
  ): ValidationResult[Option[TenantId]] =
    if (memberInfo.tenant.nonEmpty) validateTenant(memberInfo.tenant.get).map(Some(_))
    else NoOrInvalidTenantAssociated(memberInfo.tenant).invalidNec

  private def validateTenant(
    tenantId: TenantId
  ): ValidationResult[TenantId] = {
    if (tenantId.id.isEmpty) {
      TenantIdIsEmpty.invalidNec
    } else {
      tenantId.validNec
    }
  }

  private def validateTenantOpt(
    tenantIdOpt: Option[TenantId]
  ): ValidationResult[Option[TenantId]] = {
    tenantIdOpt.fold[ValidationResult[Option[TenantId]]](tenantIdOpt.validNec) {
      tenantId => validateTenant(tenantId).map(Some(_))
    }
  }


  private def validatePhoneNo(
    phoneOpt: Option[String]
  ): ValidationResult[Option[String]] = {
    if (phoneOpt.nonEmpty && phoneOpt.get.nonEmpty) phoneOpt.validNec
    else MissingOrInvalidPhoneNumber(phoneOpt).invalidNec
  }

  private def validateEmailAddress(
    emailAddressOpt: Option[String]
  ): ValidationResult[Option[String]] =
    if (emailAddressOpt.nonEmpty && emailAddressOpt.get.nonEmpty) emailAddressOpt.validNec
    else MissingOrInvalidEmailAddress(emailAddressOpt).invalidNec

  private def validateRequiredContact(
    memberInfo: MemberInfo
  ): ValidationResult[Option[Contact]] =
    if (memberInfo.contact.isEmpty) NoContactAssociated.invalidNec
    else {
      validateContact(memberInfo.contact.get, memberInfo.notificationPreference).map(Some(_))
    }

  private def validateContact(
    contact: Contact,
    notificationPreferenceOpt: Option[NotificationPreference]
  ): ValidationResult[Contact] =
    (
      validateFirstName(contact.firstName),
      validateLastName(contact.lastName),
      if (notificationPreferenceOpt.contains(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL))
        validateEmailAddress(contact.emailAddress)
      else contact.emailAddress.validNec,
      if (notificationPreferenceOpt.contains(NotificationPreference.NOTIFICATION_PREFERENCE_SMS))
        validatePhoneNo(contact.phone)
      else contact.phone.validNec,
      contact.userName.validNec,
      contact.unknownFields.validNec
    ).mapN(Contact.apply)

  private def validateContactOpt(
    contactOpt: Option[Contact],
    notificationPreferenceOpt: Option[NotificationPreference]
  ): ValidationResult[Option[Contact]] = {
    contactOpt.fold[ValidationResult[Option[Contact]]](contactOpt.validNec) {
      contact => validateContact(contact, notificationPreferenceOpt).map(Some(_))
    }
  }

  private def validateOrganizationMembership(
    organizationMembership: Seq[OrganizationId]
  ): ValidationResult[Seq[OrganizationId]] = {
    if (organizationMembership.exists(_.id.isEmpty)) {
      EmptyOrganizationIdFound.invalidNec
    } else {
      organizationMembership.validNec
    }
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
      validateRequiredContact(memberInfo),
      memberInfo.organizationMembership.validNec,
      validateRequiredTenant(memberInfo),
      memberInfo.unknownFields.validNec
    ).mapN(MemberInfo.apply)

  def validateEditableInfo(
    editableInfo: EditableInfo
  ): ValidationResult[EditableInfo] =
    (
      editableInfo.handle.validNec,
      editableInfo.avatarUrl.validNec,
      validateFirstNameOpt(editableInfo.firstName),
      validateLastNameOpt(editableInfo.lastName),
      editableInfo.notificationPreference.validNec,
      validateContactOpt(editableInfo.contact, editableInfo.notificationPreference),
      validateOrganizationMembership(editableInfo.organizationMembership),
      validateTenantOpt(editableInfo.tenant),
      editableInfo.unknownFields.validNec
    ).mapN(EditableInfo.apply)

  def validateMemberInfoOpt(
    infoOpt: Option[MemberInfo]
  ): ValidationResult[Option[MemberInfo]] =
    infoOpt.fold[ ValidationResult[Option[MemberInfo]]](NoMemberInfoAssociated.invalidNec) {
      validateMemberInfo(_).map(Some(_))
    }

  def validateEditableInfoOpt(
    infoOpt: Option[EditableInfo]
  ): ValidationResult[Option[EditableInfo]] =
    infoOpt.fold[ValidationResult[Option[EditableInfo]]](NoEditableInfoAssociated.invalidNec) {
      validateEditableInfo(_).map(Some(_))
    }

  def validateRegisterMember(
    registerMember: RegisterMember
  ): ValidationResult[RegisterMember] = (
    (
      validateMemberIdOpt(registerMember.memberId),
      validateMemberInfoOpt(registerMember.memberInfo),
      validateMemberIdOpt(registerMember.registeringMember),
      registerMember.unknownFields.validNec
    ).mapN(RegisterMember.apply)
  )

  def validateActivateMemberCommand(
    activateMember: ActivateMember
  ): ValidationResult[ActivateMember] = (
    (
      validateMemberIdOpt(activateMember.memberId),
      validateMemberIdOpt(activateMember.activatingMember),
      activateMember.unknownFields.validNec
    ).mapN(ActivateMember.apply)
  )

  def validateSuspendMemberCommand(
    suspendMember: SuspendMember
  ): ValidationResult[SuspendMember] = (
    (
      validateMemberIdOpt(suspendMember.memberId),
      validateMemberIdOpt(suspendMember.suspendingMember),
      suspendMember.unknownFields.validNec
    ).mapN(SuspendMember.apply)
  )

  def validateTerminateMemberCommand(
    terminateMember: TerminateMember
  ): ValidationResult[TerminateMember] = (
    (
      validateMemberIdOpt(terminateMember.memberId),
      validateMemberIdOpt(terminateMember.terminatingMember),
      terminateMember.unknownFields.validNec
    ).mapN(TerminateMember.apply)
    )

  def validateEditMemberInfo(
    editMemberInfo: EditMemberInfo
  ): ValidationResult[EditMemberInfo] = (
    (
      validateMemberIdOpt(editMemberInfo.memberId),
      validateEditableInfoOpt(editMemberInfo.memberInfo),
      validateMemberIdOpt(editMemberInfo.editingMember),
      editMemberInfo.unknownFields.validNec
    ).mapN(EditMemberInfo.apply)
    )

  def validateGetMemberInfo(
    getMemberInfo: GetMemberInfo
  ): ValidationResult[GetMemberInfo] = (
    (
      validateMemberIdOpt(getMemberInfo.memberId),
      getMemberInfo.unknownFields.validNec
    ).mapN(GetMemberInfo.apply)
  )
}