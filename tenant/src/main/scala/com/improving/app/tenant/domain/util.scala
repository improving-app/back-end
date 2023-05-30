package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{AddressUtil, ContactUtil}

object util {
  def infoFromEditableInfo(editable: EditableTenantInfo): TenantInfo = TenantInfo(
    name = editable.getName,
    primaryContact = editable.getPrimaryContact.toContact,
    address = editable.getAddress.toAddress,
    organizations = editable.getOrganizations
  )
}
