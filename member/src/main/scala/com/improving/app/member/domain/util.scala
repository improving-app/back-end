package com.improving.app.member.domain

import com.improving.app.common.domain.util.{ContactUtil, EditableContactUtil}

object util {
  implicit class MemberInfoUtil(info: MemberInfo) {
    private[domain] def updateInfo(editableInfo: EditableInfo): MemberInfo = {
      MemberInfo(
        handle = editableInfo.handle.getOrElse(info.handle),
        avatarUrl = editableInfo.avatarUrl.getOrElse(info.avatarUrl),
        firstName = editableInfo.firstName.getOrElse(info.firstName),
        lastName = editableInfo.lastName.getOrElse(info.lastName),
        notificationPreference = editableInfo.notificationPreference.orElse(info.notificationPreference),
        contact =
          editableInfo.contact.map(editable => info.contact.map(_.updateContact(editable))).getOrElse(info.contact),
        organizationMembership =
          if (editableInfo.organizationMembership.nonEmpty) editableInfo.organizationMembership
          else info.organizationMembership,
        tenant = editableInfo.tenant.orElse(info.tenant)
      )
    }

    private[domain] def toEditable: EditableInfo = EditableInfo(
      contact = info.contact.map(_.toEditable),
      handle = Some(info.handle),
      avatarUrl = Some(info.avatarUrl),
      firstName = Some(info.firstName),
      lastName = Some(info.lastName),
      tenant = info.tenant,
      notificationPreference = info.notificationPreference,
      organizationMembership = info.organizationMembership
    )
  }

  implicit class EditableMemberInfoUtil(info: EditableInfo) {
    private[domain] def updateInfo(editableInfo: EditableInfo): EditableInfo = {
      EditableInfo(
        handle = editableInfo.handle.orElse(info.handle),
        avatarUrl = editableInfo.avatarUrl.orElse(info.avatarUrl),
        firstName = editableInfo.firstName.orElse(info.firstName),
        lastName = editableInfo.lastName.orElse(info.lastName),
        notificationPreference = editableInfo.notificationPreference,
        contact =
          editableInfo.contact.map(editable => info.contact.map(_.updateContact(editable))).getOrElse(info.contact),
        organizationMembership =
          if (editableInfo.organizationMembership.isEmpty) info.organizationMembership
          else editableInfo.organizationMembership,
        tenant = editableInfo.tenant.orElse(info.tenant)
      )
    }

    private[domain] def toInfo: MemberInfo = MemberInfo(
      contact = info.contact.map(_.toContact),
      handle = info.getHandle,
      avatarUrl = info.getAvatarUrl,
      firstName = info.getFirstName,
      lastName = info.getLastName,
      tenant = info.tenant,
      notificationPreference = info.notificationPreference,
      organizationMembership = info.organizationMembership
    )
  }
}
