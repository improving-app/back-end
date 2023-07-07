package com.improving.app.store.domain

import com.improving.app.common.errors.Validation._

object Validation {
  val draftTransitionStoreInfoValidator: Validator[EditableStoreInfo] =
    applyAllValidators[EditableStoreInfo](
      storeInfo => required("name")(storeInfo.name),
      storeInfo => required("description")(storeInfo.description),
      storeInfo => required("sponsoring org")(storeInfo.sponsoringOrg)
    )

  val storeCommandValidator: Validator[StoreRequest] =
    applyAllValidators[StoreRequest](
      storeReq => required("storeId")(storeReq.storeId),
      storeReq => required("onBehalfOf")(storeReq.onBehalfOf)
    )
}
