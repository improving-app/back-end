package com.improving.app.store.domain

import com.improving.app.common.domain.OrganizationId
import com.improving.app.common.errors.Validation._
import com.improving.app.common.errors.ValidationError

object StoreValidation {
  val storeRequestValidator: Validator[StoreRequest] =
    applyAllValidators[StoreRequest](
      Seq(
        r => required("store id", storeIdValidator)(r.storeId),
        r => required("on behalf of", memberIdValidator)(r.onBehalfOf)
      )
    )

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

  val inactiveStateStoreInfoValidator: Validator[StoreInfo] =
    applyAllValidators[StoreInfo](
      Seq(
        storeInfo => storeNameValidator(storeInfo.name),
        storeInfo => storeDescriptionValidator(storeInfo.description),
        storeInfo => optional(storeSponsoringOrgValidator)(storeInfo.sponsoringOrg)
      )
    )

  val activeStateStoreInfoValidator: Validator[StoreInfo] =
    applyAllValidators[StoreInfo](
      Seq(
        inactiveStateStoreInfoValidator,
        storeInfo => required("sponsoring org", storeSponsoringOrgValidator)(storeInfo.sponsoringOrg)
      )
    )
}
