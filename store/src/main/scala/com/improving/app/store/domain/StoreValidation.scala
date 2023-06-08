package com.improving.app.store.domain

import com.improving.app.common.domain.OrganizationId
import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object StoreValidation {
  val storeNameValidator: Validator[String] = name => {
    if (name.isEmpty) {
      Some(ValidationError("name empty"))
    } else {
      None
    }
  }

  val storeDescriptionValidator: Validator[String] = description => {
    if (description.isEmpty) {
      Some(ValidationError("description empty"))
    } else {
      None
    }
  }

  val storeSponsoringOrgValidator: Validator[OrganizationId] = sponsoringOrg => {
    if (sponsoringOrg.id.isEmpty) {
      Some(ValidationError("id empty"))
    } else {
      None
    }
  }

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
