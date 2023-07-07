package com.improving.app.organization.domain

import com.improving.app.common.domain.util.{AddressUtil, EditableAddressUtil}

object util {

  implicit class OrganizationInfoUtil(orgInfo: OrganizationInfo) {
    private[domain] def toEditable: EditableOrganizationInfo =
      EditableOrganizationInfo(
        name = Some(orgInfo.name),
        shortName = orgInfo.shortName,
        isPublic = Some(orgInfo.isPublic),
        address = orgInfo.address.map(_.toEditable),
        tenant = orgInfo.tenant,
        url = orgInfo.url,
        logo = orgInfo.logo
      )

    private[domain] def updateInfo(editableInfo: EditableOrganizationInfo): OrganizationInfo =
      OrganizationInfo(
        name = editableInfo.name.getOrElse(orgInfo.name),
        shortName = editableInfo.shortName.orElse(orgInfo.shortName),
        isPublic = editableInfo.isPublic.getOrElse(orgInfo.isPublic),
        address = editableInfo.address
          .map(editable => orgInfo.address.map(_.updateAddress(editable)))
          .getOrElse(orgInfo.address),
        tenant = editableInfo.tenant.orElse(orgInfo.tenant),
        url = editableInfo.url.orElse(orgInfo.url),
        logo = editableInfo.logo.orElse(orgInfo.logo)
      )
  }

  implicit class EditableOrganizationInfoUtil(editableInfo: EditableOrganizationInfo) {
    private[domain] def toInfo: OrganizationInfo =
      OrganizationInfo(
        name = editableInfo.getName,
        shortName = editableInfo.shortName,
        isPublic = editableInfo.getIsPublic,
        address = editableInfo.address.map(_.toAddress),
        tenant = editableInfo.tenant,
        url = editableInfo.url,
        logo = editableInfo.logo
      )

    private[domain] def updateInfo(
        info: EditableOrganizationInfo
    ): EditableOrganizationInfo =
      EditableOrganizationInfo(
        name = info.name.orElse(editableInfo.name),
        shortName = info.shortName.orElse(editableInfo.shortName),
        isPublic = info.isPublic.orElse(editableInfo.isPublic),
        address = info.address
          .map(editable => editableInfo.address.map(_.updateAddress(editable)))
          .getOrElse(editableInfo.address),
        tenant = info.tenant.orElse(editableInfo.tenant),
        url = info.url.orElse(editableInfo.url),
        logo = info.logo.orElse(editableInfo.logo)
      )
  }
}
