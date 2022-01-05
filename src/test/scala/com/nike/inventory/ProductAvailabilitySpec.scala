package com.nike.inventory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.nike.inventory.ProductAvailability.{AddItemCommand, GetProductAvailabilityCommand, ProductAvailabilityReply, RemoveItemCommand, Reply}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class ProductAvailabilitySpec
  extends ScalaTestWithActorTestKit(ConfigFactory.load("test-application.conf"))
    with AnyWordSpecLike {

  "Product Availability" should {

    "get" in {
      val Sku = "testsku1"
      val productAvailability = testKit.spawn(ProductAvailability(Sku, "product-availability"))
      val probe = testKit.createTestProbe[Reply]
      productAvailability ! GetProductAvailabilityCommand(Sku, probe.ref)
      probe.expectMessage(ProductAvailabilityReply(Sku, 0))
    }

    "addItem" in {
      val Sku = "testsku2"
      val productAvailability = testKit.spawn(ProductAvailability(Sku, "product-availability"))
      val probe = testKit.createTestProbe[Reply]
      productAvailability ! AddItemCommand(Sku)
      productAvailability ! GetProductAvailabilityCommand(Sku, probe.ref)
      probe.expectMessage(ProductAvailabilityReply(Sku, 1))
    }

    "removeItem" in {
      val Sku = "testsku3"
      val productAvailability = testKit.spawn(ProductAvailability(Sku, "product-availability"))
      val probe = testKit.createTestProbe[Reply]
      productAvailability ! AddItemCommand(Sku)
      productAvailability ! AddItemCommand(Sku)
      productAvailability ! GetProductAvailabilityCommand(Sku, probe.ref)
      probe.expectMessage(ProductAvailabilityReply(Sku, 2))
      productAvailability ! RemoveItemCommand(Sku)
      productAvailability ! GetProductAvailabilityCommand(Sku, probe.ref)
      probe.expectMessage(ProductAvailabilityReply(Sku, 1))
    }
  }
}
