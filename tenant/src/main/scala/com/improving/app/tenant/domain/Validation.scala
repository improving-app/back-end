package com.improving.app.tenant.domain

import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object Validation {

  val organizationsValidator: Validator[TenantOrganizationList] = organizationList => {
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
    applyAllValidators[TenantInfo](
      ti => tenantNameValidator(ti.name),
      ti => required("primary contact", contactValidator)(ti.primaryContact),
      ti => required("address", addressValidator)(ti.address),
      ti => required("organizations", organizationsValidator)(ti.organizations)
    )(tenantInfo)
  }

  val partialTenantInfoValidator: Validator[TenantInfo] = tenantInfo => {
    applyAllValidators[TenantInfo](
      ti => skipEmpty(tenantNameValidator)(ti.name),
      ti => optional(contactValidator)(ti.primaryContact),
      ti => optional(addressValidator)(ti.address),
      ti => optional(organizationsValidator)(ti.organizations)
    )(tenantInfo)
  }

}
