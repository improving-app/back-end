package com.improving.app.product.domain

import com.improving.app.common.domain.util.{ContactUtil, EditableContactUtil}

object util {
  implicit class ProductInfoUtil(info: ProductInfo) {
    private[domain] def updateInfo(editableInfo: EditableProductInfo): ProductInfo = {
      ProductInfo(
        productName = editableInfo.productName.getOrElse(info.productName),
        productDetails = editableInfo.productDetails.orElse(info.productDetails),
        image = if (editableInfo.image.isEmpty) info.image else editableInfo.image,
        price = editableInfo.price.getOrElse(info.price),
        cost = editableInfo.cost.getOrElse(info.cost),
        eventId = editableInfo.eventId.orElse(info.eventId)
      )
    }

    private[domain] def toEditable: EditableProductInfo = EditableProductInfo(
      productName = Some(info.productName),
      productDetails = info.productDetails,
      image = info.image,
      price = Some(info.price),
      cost = Some(info.cost),
      eventId = info.eventId
    )
  }

  implicit class EditableProductInfoUtil(info: EditableProductInfo) {
    private[domain] def updateInfo(editableInfo: EditableProductInfo): EditableProductInfo = {
      EditableProductInfo(
        productName = editableInfo.productName.orElse(info.productName),
        productDetails = editableInfo.productDetails.orElse(info.productDetails),
        image = if (editableInfo.image.isEmpty) info.image else editableInfo.image,
        price = editableInfo.price.orElse(info.price),
        cost = editableInfo.cost.orElse(info.cost),
        eventId = editableInfo.eventId.orElse(info.eventId)
      )
    }

    private[domain] def toInfo: ProductInfo = ProductInfo(
      productName = info.getProductName,
      productDetails = info.productDetails,
      image = info.image,
      price = info.getPrice,
      cost = info.getCost,
      eventId = info.eventId
    )
  }
}
