package com.improving.app.organization.domain

import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object OrganizationValidation {
  val organizationNameValidator: Validator[String] = name => {
    if (name.isEmpty) {
      Some(ValidationError("name empty"))
    } else {
      None
    }
  }

  val completeOrganizationInfoValidator: Validator[OrganizationInfo] =
    applyAllValidators[OrganizationInfo](Seq(
      orgInfo => organizationNameValidator(orgInfo.name),
      orgInfo => organizationNameValidator(orgInfo.shortName),
      orgInfo => required("tenant", tenantIdValidator)(orgInfo.tenant),
      orgInfo => required("address", addressValidator)(orgInfo.address),
      orgInfo => urlValidator(orgInfo.url),
      orgInfo => urlValidator(orgInfo.logo)
    ))
}
