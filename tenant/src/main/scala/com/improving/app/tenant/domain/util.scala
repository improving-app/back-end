package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{EditableAddressUtil, EditableContactUtil}

object util {

  implicit class EditableInfoUtil(editable: EditableTenantInfo) {
    implicit def toInfo: TenantInfo = TenantInfo(
      name = editable.getName,
      primaryContact = editable.primaryContact.map(_.toContact),
      address = editable.address.map(_.toAddress),
      organizations = editable.organizations
    )
  }
}
