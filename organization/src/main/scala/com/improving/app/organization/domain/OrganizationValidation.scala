package com.improving.app.organization.domain

import cats.data.{Validated, ValidatedNec}
import cats.implicits._
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.organization._
import com.improving.app.organization.repository.OrganizationRepository

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}


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

  case class ParentNotInOrgError(parentId: String) extends OrganizationValidationError {
    val errorMessage: String =
      s"New parent organization with id $parentId is not a descendant of this organization's root"
  }

  case class ParentIsDescendantError(parentId: String) extends OrganizationValidationError {
    val errorMessage: String =
      s"New parent organization with id $parentId is a descendant of this organization"
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


  private def validateNewParent(newParentId: String, orgId: String, repo: OrganizationRepository)(implicit ec: ExecutionContext): ValidationResult[_] = {
    val fRes = for {
      root <- repo.getRootOrganization(orgId)
      descendants <- repo.getDescendants(root)
      localDescendants <- repo.getDescendants(orgId)
      isInOrg = descendants.contains(newParentId)
      isNotCircular = !localDescendants.contains(newParentId)
    } yield
      (if (isInOrg) ().validNec else ParentNotInOrgError(newParentId).invalidNec,
        if (isNotCircular) ().validNec else ParentIsDescendantError(newParentId).invalidNec).mapN((_, _) => ())
    Await.result(fRes, 5.minutes)
  }

  def validateUpdateParentRequest(request: UpdateParentRequest, maybeRepo: Option[OrganizationRepository])(implicit ec: ExecutionContext): ValidationResult[UpdateParentRequest] = {
    val basicValidation = validateBasicRequest(request)
    //To some extent this defeats the purpose of the validation pattern, but I think it's best not to query the projections unless we have to
    basicValidation match {
      case Validated.Valid(_) =>
        (maybeRepo, request.newParent, request.orgId) match {
          case (Some(repo), Some(newParent), Some(orgId)) =>
            validateNewParent(newParent.id, orgId.id, repo).map(_ => request)
          case _ => basicValidation
        }
      case _ => basicValidation
    }
  }

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
  def validateEstablishOrganizationRequest(request: EstablishOrganizationRequest, orgId: Option[OrganizationId], maybeRepo: Option[OrganizationRepository])(implicit ec: ExecutionContext): ValidationResult[EstablishOrganizationRequest] = {
    val presentValidation = (validateNonEmpty(request.actingMember, "actingMember"), validateNonEmpty(request.info, "info"),
      validateInfo(request.info.getOrElse(Info())), validateNonEmpty(request.owners, "owners")).mapN((_, _, _, _)  => request)
    presentValidation match {
      case Validated.Valid(_) => (request.parent, orgId, maybeRepo) match {
        case (Some(parent), Some(orgId), Some(repo)) => validateNewParent(parent.id, orgId.id, repo).map(_  => request)
        case _ => presentValidation
      }
      case _ => presentValidation
    }
  }

  def validateGetOrganizationByIdRequest(request: GetOrganizationByIdRequest): ValidationResult[GetOrganizationByIdRequest] =
    validateNonEmpty(request.orgId, "orgId").map(_  => request)

  def validateGetOrganizationInfoRequest(request: GetOrganizationInfoRequest): ValidationResult[GetOrganizationInfoRequest] =
    validateNonEmpty(request.orgId, "orgId").map(_  => request)


}
