package com.improving.app.product.domain

import com.improving.app.common.errors.Validation.{
  applyAllValidators,
  endBeforeStartValidator,
  listHasLength,
  required,
  requiredThenValidate,
  Validator
}

object Validation {
  val draftTransitionProductInfoValidator: Validator[EditableProductInfo] =
    applyAllValidators[EditableProductInfo](
      productInfo => required("product_name")(productInfo.productName),
      productInfo => required("product_details")(productInfo.productDetails),
      productInfo => listHasLength("image")(productInfo.image),
      productInfo => required("price")(productInfo.price),
      productInfo => required("cost")(productInfo.cost),
      productInfo => required("event_id")(productInfo.eventId),
    )

  val productCommandValidator: Validator[ProductCommand] =
    applyAllValidators[ProductCommand](
      productCommand => required("sku")(productCommand.sku),
      productCommand => required("on_behalf_of")(productCommand.onBehalfOf)
    )

  val productQueryValidator: Validator[ProductQuery] =
    applyAllValidators[ProductQuery](productQuery => required("sku")(productQuery.sku))
}
