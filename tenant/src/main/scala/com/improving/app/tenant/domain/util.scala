package com.improving.app.tenant.domain

import com.improving.app.common.domain.util.{addressFromEditableAddress, contactFromEditableContact}

object util {
  def infoFromEditableInfo(infoToUpdate: EditableTenantInfo, infoToCopy: TenantInfo): TenantInfo =
    infoToCopy
      .copy(
        name = infoToUpdate.name.getOrElse(infoToCopy.name),
        address = infoToUpdate.address
          .map(addressFromEditableAddress(_, infoToCopy.address))
          .getOrElse(infoToCopy.address),
        primaryContact = infoToUpdate.primaryContact
          .map(contactFromEditableContact(_, infoToCopy.primaryContact))
          .getOrElse(infoToCopy.primaryContact),
        organizations = infoToUpdate.organizations.getOrElse(infoToCopy.organizations)
      )
}
