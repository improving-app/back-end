package com.nike.inventory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.nike.inventory.ProductAvailability.{GetProductAvailabilityCommand, ProductAvailabilityReply, Reply}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class ProductAvailabilitySpec
  extends ScalaTestWithActorTestKit(ConfigFactory.load("test-application.conf"))
    with AnyWordSpecLike {

  "Product Availability" should {

    "get" in {
      val productAvailability = testKit.spawn(ProductAvailability("12345"))
      val probe = testKit.createTestProbe[Reply]
      productAvailability ! GetProductAvailabilityCommand("12345", probe.ref)
      probe.expectMessage(ProductAvailabilityReply("12345", "", "", 0))
    }
  }
}
