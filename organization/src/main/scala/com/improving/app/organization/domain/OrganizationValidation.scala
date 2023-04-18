package com.improving.app.organization.domain

import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object OrganizationValidation {
  val organizationRequestValidator: Validator[OrganizationRequest] =
    applyAllValidators[OrganizationRequest](Seq(
      r => required("organization id", organizationIdValidator)(r.organizationId),
      r => required("on behalf of", memberIdValidator)(r.onBehalfOf)
    ))

  val organizationNameValidator: Validator[String] = name => {
    if (name.isEmpty) {
      Some(ValidationError("name empty"))
    } else {
      None
    }
  }

  val inactiveStateOrganizationInfoValidator: Validator[OrganizationInfo] =
    applyAllValidators[OrganizationInfo](Seq(
      orgInfo => organizationNameValidator(orgInfo.name),
      orgInfo => organizationNameValidator(orgInfo.shortName),
      orgInfo => required("tenant", tenantIdValidator)(orgInfo.tenant),
      orgInfo => optional(addressValidator)(orgInfo.address),
      orgInfo => urlValidator(orgInfo.url),
      orgInfo => urlValidator(orgInfo.logo)
    ))

  val activeStateOrganizationInfoValidator: Validator[OrganizationInfo] =
    applyAllValidators[OrganizationInfo](Seq(
      inactiveStateOrganizationInfoValidator,
      orgInfo => required("address", addressValidator)(orgInfo.address),
    ))
}
