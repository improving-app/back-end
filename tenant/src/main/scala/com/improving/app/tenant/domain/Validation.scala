package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{addressFromEditableAddress, contactFromEditableContact}
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
      ti => required("name", tenantNameValidator)(ti.name),
      ti => required("primary contact", contactValidator)(ti.primaryContact.map(contactFromEditableContact)),
      ti => required("address", addressValidator)(ti.address.map(addressFromEditableAddress)),
      ti => required("organizations", organizationsValidator)(ti.organizations)
    )(tenantInfo)
  }

}
