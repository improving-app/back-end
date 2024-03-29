package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{EditableAddressUtil, EditableContactUtil}
import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object Validation {
  val draftTransitionTenantInfoValidator: Validator[EditableTenantInfo] = tenantInfo => {
    applyAllValidators[EditableTenantInfo](
      ti => required("name")(ti.name),
      ti => required("primary contact")(ti.primaryContact.map(_.toContact)),
      ti => requiredThenValidate("address", editableAddressValidator)(ti.address),
      ti => required("organizations")(ti.organizations)
    )(tenantInfo)
  }

  val tenantRequestValidator: Validator[TenantRequest] = command => {
    applyAllValidators[TenantRequest](c => required("name")(c.tenantId.map(_.id)))(command)
  }

  val tenantCommandValidator: Validator[TenantCommand] = command => {
    applyAllValidators[TenantCommand](c => required("primary contact")(c.onBehalfOf.map(_.id)))(command)
  }
}
