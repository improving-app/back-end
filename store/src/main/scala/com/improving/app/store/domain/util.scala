package com.improving.app.store.domain

object util {

  implicit class StoreInfoUtil(info: StoreInfo) {
    private[domain] def updateInfo(
        newInfo: EditableStoreInfo
    ): StoreInfo = {
      StoreInfo(
        name = newInfo.name.getOrElse(info.name),
        description = newInfo.description.getOrElse(info.description),
        products = if (newInfo.products.isEmpty) info.products else newInfo.products,
        event = newInfo.event.orElse(info.event),
        sponsoringOrg = newInfo.sponsoringOrg.orElse(info.sponsoringOrg)
      )
    }

    private[domain] def toEditable: EditableStoreInfo = EditableStoreInfo(
      name = Some(info.name),
      description = Some(info.description),
      products = info.products,
      event = info.event,
      sponsoringOrg = info.sponsoringOrg
    )
  }

  implicit class EditableStoreInfoUtil(info: EditableStoreInfo) {
    private[domain] def updateInfo(
        newInfo: EditableStoreInfo
    ): EditableStoreInfo = {
      EditableStoreInfo(
        name = newInfo.name.orElse(info.name),
        description = newInfo.description.orElse(info.description),
        products = if (newInfo.products.isEmpty) info.products else newInfo.products,
        event = newInfo.event.orElse(info.event),
        sponsoringOrg = newInfo.sponsoringOrg.orElse(info.sponsoringOrg)
      )
    }

    private[domain] def toInfo: StoreInfo = StoreInfo(
      name = info.getName,
      description = info.getDescription,
      products = info.products,
      event = info.event,
      sponsoringOrg = info.sponsoringOrg
    )
  }
}
