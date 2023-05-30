package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{EditableAddressUtil, EditableContactUtil}

object util {

  implicit class EditableInfoUtil(editable: EditableTenantInfo) {
    implicit def toInfo: TenantInfo = TenantInfo(
      name = editable.getName,
      primaryContact = editable.getPrimaryContact.toContact,
      address = editable.getAddress.toAddress,
      organizations = editable.getOrganizations
    )
  }
}
