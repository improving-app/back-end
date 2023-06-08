package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{EditableAddressUtil, EditableContactUtil}
import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object Validation {

  val organizationsValidator: Validator[TenantOrganizationList] = organizationList => {
    None
  }

  val tenantNameValidator: Validator[String] = name => {
    if (name.isEmpty) {
      Some(ValidationError("name empty"))
    } else {
      None
    }
  }

  val completeEditableTenantInfoValidator: Validator[EditableTenantInfo] = tenantInfo => {
    applyAllValidators[EditableTenantInfo](
      ti => requiredThenValidate("name", tenantNameValidator)(ti.name),
      ti => requiredThenValidate("primary contact", contactValidator)(ti.primaryContact.map(_.toContact)),
      ti => requiredThenValidate("address", addressValidator)(ti.address.map(_.toAddress)),
      ti => requiredThenValidate("organizations", organizationsValidator)(ti.organizations)
    )(tenantInfo)
  }

}
