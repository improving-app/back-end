package com.improving.app.product.domain

import com.google.protobuf.duration.Duration
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.common.domain.{EventId, MemberId, OrganizationId, Sku}
import com.improving.app.product.domain.ProductInfo
import com.improving.app.product.domain.util.ProductInfoUtil

import java.time.Instant
import java.util.UUID

object TestData {
  val testSkuString: String = UUID.randomUUID().toString
  val testEventIdString: String = UUID.randomUUID().toString

  val now: Instant = Instant.now()

  val baseProductInfo: ProductInfo = ProductInfo(
    productName = "Product Name",
    productDetails = Some(
      ProductDetails(
        ProductDetails.Value
          .TicketDetails(
            TicketDetails(TicketDetails.Value.OpenTicketInfo(OpenTicketInfo()))
          )
      )
    ),
    image = Seq("img.png"),
    eventId = Some(EventId(testEventIdString))
  )

  val baseEditableInfo: EditableProductInfo = EditableProductInfo(
    productName = Some("Product Name"),
    productDetails = Some(
      ProductDetails(
        ProductDetails.Value
          .TicketDetails(
            TicketDetails(TicketDetails.Value.OpenTicketInfo(OpenTicketInfo()))
          )
      )
    ),
    image = Seq("img.png"),
    price = Some(1.5),
    cost = Some(0.5),
    eventId = Some(EventId(testEventIdString))
  )

  val baseCreateProduct: CreateProduct = CreateProduct(
    sku = Some(Sku(testSkuString)),
    info = Some(baseProductInfo.toEditable),
    onBehalfOf = Some(MemberId("creatingMember"))
  )

  val baseActivateProduct: ActivateProduct = ActivateProduct(
    sku = Some(Sku(testSkuString)),
    onBehalfOf = Some(MemberId("activatingMember"))
  )

  val baseInactivateProduct: InactivateProduct =
    InactivateProduct(sku = Some(Sku(testSkuString)), onBehalfOf = Some(MemberId("inactivatingMember")))

  val baseDeleteProduct: DeleteProduct =
    DeleteProduct(sku = Some(Sku(testSkuString)), onBehalfOf = Some(MemberId("deletingMember")))

  val baseEditProductInfo: EditProductInfo = EditProductInfo(
    sku = Some(Sku(testSkuString)),
    info = Some(baseEditableInfo),
    onBehalfOf = Some(MemberId("editingMember"))
  )
}
