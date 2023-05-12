package com.improving.app.tenant.domain

import com.improving.app.common.domain
import com.improving.app.common.domain.util.{addressFromEditableAddress, contactFromEditableContact}
import com.improving.app.common.domain.{Address, Contact, EditableAddress, EditableContact}
import com.improving.app.common.service.util.{doForSameIfHas, doIfHas}

object util {
  def infoFromEditableInfo(infoToUpdate: EditableTenantInfo, infoToCopy: TenantInfo): TenantInfo =
    infoToCopy
      .copy(
        name = doForSameIfHas[String](infoToUpdate.name, infoToCopy.name),
        address = doIfHas[EditableAddress, Address](
          infoToUpdate.address,
          infoToCopy.address,
          v => addressFromEditableAddress(v, infoToCopy.address)
        ),
        primaryContact = doIfHas[EditableContact, Contact](
          infoToUpdate.primaryContact,
          infoToCopy.primaryContact,
          v => contactFromEditableContact(v, infoToCopy.primaryContact)
        ),
        organizations =
          if (infoToUpdate.organizations.isDefined) infoToUpdate.getOrganizations else infoToCopy.organizations
      )
}
