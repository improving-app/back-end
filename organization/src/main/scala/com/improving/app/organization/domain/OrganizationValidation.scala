package com.improving.app.organization.domain

import cats.data.ValidatedNec
import cats.implicits._
import com.improving.app.common.domain.MemberId
import com.improving.app.organization._
import com.improving.app.organization.domain.{HasActingMember, HasOrganizationId}


/**
 * Organization(
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

  case class FieldIsEmptyError(fieldName: String) extends OrganizationValidationError {
    val errorMessage: String =
      s"$fieldName cannot be empty"
  }

  private def validateNonEmpty[T <: IterableOnce[_]](field: T, fieldName: String): ValidationResult[T] = {
    if(field.iterator.nonEmpty) field.validNec
    else FieldIsEmptyError(fieldName).invalidNec
  }

  private def validateNonEmptyStringOption(
                                      field: String,
                                      value: Option[String]
                                    ): ValidationResult[Option[String]] =
    if (value.exists(_.nonEmpty)) value.validNec
    else FieldIsEmptyError(field).invalidNec

  def validateInfo(
      info: Info
  ): ValidationResult[Info] =
    (
      validateNonEmptyStringOption( "name in info", info.name),
      info.shortName.validNec,
      info.address.validNec,
      info.isPrivate.validNec,
      info.url.validNec,
      info.logo.validNec,
      validateNonEmpty(info.tenant, "tenant in info")
    ).mapN((name, shortName, address, isPrivate, url, logo, tenant) =>
      Info(name, shortName, address, isPrivate, url, logo, tenant)
    )

  def validateBasicRequest[T <: HasOrganizationId with HasActingMember](request: T): ValidationResult[T] =
    (validateNonEmpty(request.orgId, "orgId"), validateNonEmpty(request.actingMember, "acting member")).mapN((_, _)  => request)

  def validateUpdateOrganizationContactsRequest(request: UpdateOrganizationContactsRequest): ValidationResult[UpdateOrganizationContactsRequest] =
    validateBasicRequest(request).map(_  => request)

  def validateUpdateParentRequest(request: UpdateParentRequest): ValidationResult[UpdateParentRequest] =
    validateBasicRequest(request).map(_  => request)

  def validateEditOrganizationInfoRequest(request: EditOrganizationInfoRequest): ValidationResult[EditOrganizationInfoRequest] =
    (validateBasicRequest(request),
      validateNonEmpty(request.info, "info")).mapN((_, _)  => request)

  def validateRemoveOwnersFromOrganizationRequest(request: RemoveOwnersFromOrganizationRequest, existingOwners: Seq[MemberId]): ValidationResult[RemoveOwnersFromOrganizationRequest] =
    (validateBasicRequest(request),
      validateNonEmpty(existingOwners.filterNot(request.removedOwners.contains), "the result of removing the requested owners")).mapN((_, _)  => request)

  def validateAddOwnersToOrganizationRequest(request: AddOwnersToOrganizationRequest): ValidationResult[AddOwnersToOrganizationRequest] =
    validateBasicRequest(request).map(_  => request)

  def validateRemoveMembersFromOrganizationRequest(request: RemoveMembersFromOrganizationRequest): ValidationResult[RemoveMembersFromOrganizationRequest] =
    validateBasicRequest(request)
      .map(_  => request)


  def validateAddMembersToOrganizationRequest(request: AddMembersToOrganizationRequest): ValidationResult[AddMembersToOrganizationRequest] =
    validateBasicRequest(request).map(_  => request)

  //TODO verify name-uniqueness across entire organizational structure
  def validateEstablishOrganizationRequest(request: EstablishOrganizationRequest): ValidationResult[EstablishOrganizationRequest] =
    (validateNonEmpty(request.actingMember, "actingMember"), validateNonEmpty(request.info, "info"),
      validateInfo(request.info.getOrElse(Info())), validateNonEmpty(request.owners, "owners")).mapN((_, _, _, _)  => request)

  def validateGetOrganizationByIdRequest(request: GetOrganizationByIdRequest): ValidationResult[GetOrganizationByIdRequest] =
    validateNonEmpty(request.orgId, "orgId").map(_  => request)

  def validateGetOrganizationInfoRequest(request: GetOrganizationInfoRequest): ValidationResult[GetOrganizationInfoRequest] =
    validateNonEmpty(request.orgId, "orgId").map(_  => request)


}
