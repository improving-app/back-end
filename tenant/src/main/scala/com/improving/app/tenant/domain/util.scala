package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{addressFromEditableAddress, contactFromEditableContact}

object util {
  def infoFromEditableInfo(editable: EditableTenantInfo): TenantInfo = TenantInfo(
    name = editable.getName,
    primaryContact = contactFromEditableContact(editable.getPrimaryContact),
    address = addressFromEditableAddress(editable.getAddress),
    organizations = editable.getOrganizations
  )
}
