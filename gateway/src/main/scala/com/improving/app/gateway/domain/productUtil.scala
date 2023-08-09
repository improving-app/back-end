package com.improving.app.gateway.domain

import com.improving.app.product.domain.{
  EditableProductInfo,
  OpenTicketDetails,
  ProductDetails,
  ProductInfo,
  ProductMetaInfo,
  ReservedTicketDetails,
  RestrictedTicketDetails,
  TicketDetails
}
import com.improving.app.gateway.domain.demoScenario.Product
import com.improving.app.gateway.domain.product.{
  EditableProductInfo => GatewayEditableProductInfo,
  OpenTicketDetails => GatewayOpenTicketDetails,
  ProductCreated,
  ProductDetails => GatewayProductDetails,
  ProductInfo => GatewayProductInfo,
  ProductMetaInfo => GatewayProductMetaInfo,
  ProductState => GatewayProductState,
  ReservedTicketDetails => GatewayReservedTicketDetails,
  RestrictedTicketDetails => GatewayRestrictedTicketDetails,
  TicketDetails => GatewayTicketDetails
}

object productUtil {

  implicit class ProductCreatedUtil(established: ProductCreated) {
    implicit def toProduct: Product = Product(
      sku = established.sku,
      info = established.info.map(_.toInfo),
      metaInfo = established.meta
    )
  }

  implicit class GatewayEditableProductInfoUtil(info: GatewayEditableProductInfo) {

    def toInfo: GatewayProductInfo = GatewayProductInfo(
      productName = info.getProductName,
      productDetails = info.productDetails,
      image = info.image,
      price = info.getPrice,
      cost = info.getCost,
      eventId = info.eventId
    )

    def toEditableInfo: EditableProductInfo = EditableProductInfo(
      productName = info.productName,
      productDetails = info.productDetails.map(_.toProductDetails),
      image = info.image,
      price = info.price,
      cost = info.cost,
      eventId = info.eventId,
    )
  }

  implicit class EditableProductInfoUtil(info: EditableProductInfo) {

    def toGatewayEditableInfo: GatewayEditableProductInfo =
      GatewayEditableProductInfo(
        productName = info.productName,
        productDetails = info.productDetails.map(_.toGatewayProductDetails),
        image = info.image,
        price = info.price,
        cost = info.cost,
        eventId = info.eventId
      )
  }

  implicit class ProductInfoUtil(info: ProductInfo) {

    def toGatewayInfo: GatewayProductInfo =
      GatewayProductInfo(
        productName = info.productName,
        productDetails = info.productDetails.map(_.toGatewayProductDetails),
        image = info.image,
        price = info.price,
        cost = info.cost,
        eventId = info.eventId
      )
  }

  implicit class GatewayProductDetailsUtil(gatewayDetails: GatewayProductDetails) {
    def toProductDetails: ProductDetails = if (gatewayDetails.value.isTicketDetails)
      ProductDetails(
        ProductDetails.Value.TicketDetails(
          gatewayDetails.value.ticketDetails.getOrElse(GatewayTicketDetails.defaultInstance).toTicketDetails
        )
      )
    else ProductDetails.defaultInstance
  }

  implicit class GatewayTicketDetailsUtil(gatewayTicketDetails: GatewayTicketDetails) {
    def toTicketDetails: TicketDetails = if (gatewayTicketDetails.value.isReservedTicketDetails) {
      val reserved =
        gatewayTicketDetails.value.reservedTicketDetails.getOrElse(GatewayReservedTicketDetails.defaultInstance)
      TicketDetails(
        TicketDetails.Value.ReservedTicketDetails(
          ReservedTicketDetails(reserved.section, reserved.row, reserved.seat)
        )
      )
    } else if (gatewayTicketDetails.value.isRestrictedTicketDetails) {
      val restricted =
        gatewayTicketDetails.value.restrictedTicketDetails.getOrElse(GatewayRestrictedTicketDetails.defaultInstance)
      TicketDetails(
        TicketDetails.Value.RestrictedTicketDetails(
          RestrictedTicketDetails(restricted.section)
        )
      )
    } else if (gatewayTicketDetails.value.isOpenTicketDetails) {
      TicketDetails(TicketDetails.Value.OpenTicketDetails(OpenTicketDetails()))
    } else TicketDetails.defaultInstance
  }

  implicit class ProductDetailsUtil(details: ProductDetails) {
    def toGatewayProductDetails: GatewayProductDetails = {
      if (details.value.isDefined)
        if (details.value.isTicketDetails)
          GatewayProductDetails(
            GatewayProductDetails.Value.TicketDetails(
              details.value.ticketDetails.getOrElse(TicketDetails.defaultInstance).toGatewayTicketDetails
            )
          )
        else GatewayProductDetails.defaultInstance
      else GatewayProductDetails.defaultInstance
    }
  }

  implicit class TicketDetailsUtil(ticketDetails: TicketDetails) {
    def toGatewayTicketDetails: GatewayTicketDetails = if (ticketDetails.value.isReservedTicketDetails) {
      val reserved =
        ticketDetails.value.reservedTicketDetails.getOrElse(ReservedTicketDetails.defaultInstance)
      GatewayTicketDetails(
        GatewayTicketDetails.Value.ReservedTicketDetails(
          GatewayReservedTicketDetails(reserved.section, reserved.row, reserved.seat)
        )
      )
    } else if (ticketDetails.value.isRestrictedTicketDetails) {
      val restricted =
        ticketDetails.value.restrictedTicketDetails.getOrElse(RestrictedTicketDetails.defaultInstance)
      GatewayTicketDetails(
        GatewayTicketDetails.Value.RestrictedTicketDetails(
          GatewayRestrictedTicketDetails(restricted.section)
        )
      )
    } else if (ticketDetails.value.isOpenTicketDetails) {
      GatewayTicketDetails(GatewayTicketDetails.Value.OpenTicketDetails(GatewayOpenTicketDetails()))
    } else GatewayTicketDetails.defaultInstance
  }

  implicit class ProductMetaUtil(meta: ProductMetaInfo) {
    def toGatewayProductMeta: GatewayProductMetaInfo = GatewayProductMetaInfo(
      createdBy = meta.createdBy,
      createdOn = meta.createdOn,
      lastModifiedBy = meta.lastModifiedBy,
      lastModifiedOn = meta.lastModifiedOn,
      currentState = GatewayProductState.fromValue(meta.currentState.value),
    )
  }
}
