package com.improving.app.tenant

import com.improving.app.common.domain.util.{copyAddressFromEditableAddress, copyContactFromEditableContact}
import com.improving.app.tenant.domain.{EditableTenantInfo, TenantInfo}

object util {
  def copyInfoFromEditableInfo(infoToUpdate: EditableTenantInfo, infoToCopy: TenantInfo): TenantInfo =
    infoToCopy
      .copy(
        name = infoToUpdate.name.getOrElse(infoToCopy.name),
        address = infoToUpdate.address
          .map(copyAddressFromEditableAddress(_, infoToCopy.address))
          .getOrElse(infoToCopy.address),
        primaryContact = infoToUpdate.primaryContact
          .map(copyContactFromEditableContact(_, infoToCopy.primaryContact))
          .getOrElse(infoToCopy.primaryContact),
        organizations = infoToUpdate.organizations.getOrElse(infoToCopy.organizations)
      )
}
