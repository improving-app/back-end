package com.improving.app.organization.domain

import com.improving.app.common.errors.Validation.{
  applyAllValidators,
  editableAddressValidator,
  listHasLength,
  required,
  requiredThenValidate,
  Validator
}

object Validation {

  val draftTransitionOrganizationInfoValidator: Validator[EditableOrganizationInfo] =
    applyAllValidators[EditableOrganizationInfo](
      organizationInfo => required("name")(organizationInfo.name),
      organizationInfo => required("isPublic")(organizationInfo.isPublic),
      organizationInfo => requiredThenValidate("address", editableAddressValidator)(organizationInfo.address),
    )

  val organizationCommandValidator: Validator[OrganizationCommand] =
    applyAllValidators[OrganizationCommand](
      organizationCommand => required("organizationId")(organizationCommand.organizationId),
      organizationCommand => required("on_behalf_of")(organizationCommand.onBehalfOf)
    )

  val organizationQueryValidator: Validator[OrganizationQuery] =
    applyAllValidators[OrganizationQuery](organizationQuery =>
      required("organizationId")(organizationQuery.organizationId)
    )
}
