package com.improving.app.gatling.demoScenario.gen

import com.improving.app.common.domain.{MemberId, Sku}
import com.improving.app.gateway.domain.event.CreateEvent
import com.improving.app.gateway.domain.product.{
  ActivateProduct,
  CreateProduct,
  EditableProductInfo,
  OpenTicketDetails,
  ProductDetails,
  ReservedTicketDetails,
  RestrictedTicketDetails,
  TicketDetails
}

import java.util.UUID
import scala.util.Random

object productGen {

  def genRandomSection: String = Random.alphanumeric.filter(_.isLetter).head.toString
  def genRandomTicketDetails: TicketDetails.Value = Random
    .shuffle(
      Seq(
        TicketDetails.Value.OpenTicketDetails(OpenTicketDetails()),
        TicketDetails.Value
          .RestrictedTicketDetails(RestrictedTicketDetails(genRandomSection)),
        TicketDetails.Value.ReservedTicketDetails(
          ReservedTicketDetails(genRandomSection, Random.nextInt(15).toString, Random.nextInt(30).toString)
        )
      )
    )
    .head
  def genCreateProducts(
      numProductsPerStore: Int,
      creatingMember: Option[MemberId],
      eventsForOrg: Seq[CreateEvent]
  ): Seq[CreateProduct] = eventsForOrg.flatMap { event =>
    Random
      .shuffle((0 until numProductsPerStore).map(_ => Sku(UUID.randomUUID().toString)))
      .zip(
        Random
          .shuffle(
            (0 until numProductsPerStore).map(_ => creatingMember)
          )
      )
      .zip(
        (0 until numProductsPerStore).map(_ =>
          ProductDetails(ProductDetails.Value.TicketDetails(TicketDetails(genRandomTicketDetails)))
        )
      )
      .flatMap { case ((id, creatingMember), details) =>
        Some(
          CreateProduct(
            Some(id),
            Some(
              EditableProductInfo(
                productName =
                  Some(s"${event.info.flatMap(_.eventName).getOrElse("NO EVENT NAME FOUND")} event ticket of type "),
                productDetails = Some(details),
                image = Seq(),
                price = Some(0.0),
                cost = None,
                eventId = event.eventId
              )
            ),
            creatingMember
          )
        )
      }
  }

  def genActivateProduct(createProduct: CreateProduct): ActivateProduct =
    ActivateProduct(createProduct.sku, None, createProduct.onBehalfOf)
}
