package com.improving.app.store.domain

object util {

  implicit class StoreInfoUtil(info: StoreInfo) {
    private[domain] def updateInfo(
        newInfo: EditableStoreInfo
    ): StoreInfo = {
      StoreInfo(
        name = newInfo.name.getOrElse(info.name),
        description = newInfo.description.getOrElse(info.description),
        sponsoringOrg = newInfo.sponsoringOrg.orElse(info.sponsoringOrg)
      )
    }
  }

  implicit class EditableStoreInfoUtil(info: EditableStoreInfo) {
    private[domain] def updateInfo(
        newInfo: EditableStoreInfo
    ): EditableStoreInfo = {
      EditableStoreInfo(
        name = newInfo.name.orElse(info.name),
        description = newInfo.description.orElse(info.description),
        sponsoringOrg = newInfo.sponsoringOrg.orElse(info.sponsoringOrg)
      )
    }
  }
}
