package com.inventory

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.inventory.commands.v1.{Ack, AddItemCommand, GetProductAvailabilityCommand, ProductAvailabilityReply, RemoveItemCommand}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class ProductAvailabilitySpec
  extends ScalaTestWithActorTestKit(ConfigFactory.load("test-application.conf"))
    with AnyWordSpecLike {

  "Product Availability" should {

    "fail get when style/color/size does not exist in system" in {
      val Style = "test-style1"
      val Color = "blue"
      val Size = "12"
      val entityId = ProductAvailability.toPersistenceId(Style, Color, Size)
      val productAvailability = testKit.spawn(ProductAvailability(entityId, ProductAvailabilityTags.Single))
      val probe = testKit.createTestProbe[ProductAvailabilityReply]
      productAvailability ! GetProductAvailabilityCommand(entityId, Style, Color, Size, Some(probe.ref))
      probe.expectMessage(ProductAvailabilityReply("", "", "", 0))
    }

    "addItem" in {
      val Style = "test-style2"
      val Color = "blue"
      val Size = "12"
      val entityId = ProductAvailability.toPersistenceId(Style, Color, Size)
      val productAvailability = testKit.spawn(ProductAvailability(entityId, ProductAvailabilityTags.Single))
      val ackProbe = testKit.createTestProbe[Ack]
      val productAvailabilityReplyProbe = testKit.createTestProbe[ProductAvailabilityReply]
      productAvailability ! AddItemCommand(entityId, Style, Color, Size, Some(ackProbe.ref))
      ackProbe.expectMessage(Ack(entityId))
      productAvailability ! GetProductAvailabilityCommand(entityId, Style, Color, Size, Some(productAvailabilityReplyProbe.ref))
      productAvailabilityReplyProbe.expectMessage(ProductAvailabilityReply(Style, Color, Size, 1))
    }

    "removeItem" in {
      val Style = "test-style3"
      val Color = "blue"
      val Size = "12"
      val entityId = ProductAvailability.toPersistenceId(Style, Color, Size)
      val productAvailability = testKit.spawn(ProductAvailability(entityId, ProductAvailabilityTags.Single))
      val ackProbe = testKit.createTestProbe[Ack]
      val productAvailabilityReplyProbe = testKit.createTestProbe[ProductAvailabilityReply]
      productAvailability ! AddItemCommand(entityId, Style, Color, Size, Some(ackProbe.ref))
      ackProbe.expectMessage(Ack(entityId))
      productAvailability ! AddItemCommand(entityId, Style, Color, Size, Some(ackProbe.ref))
      ackProbe.expectMessage(Ack(entityId))
      productAvailability ! GetProductAvailabilityCommand(entityId, Style, Color, Size, Some(productAvailabilityReplyProbe.ref))
      productAvailabilityReplyProbe.expectMessage(ProductAvailabilityReply(Style, Color, Size, 2))
      productAvailability ! RemoveItemCommand(entityId, Style, Color, Size, Some(ackProbe.ref))
      ackProbe.expectMessage(Ack(entityId))
      productAvailability ! GetProductAvailabilityCommand(entityId, Style, Color, Size, Some(productAvailabilityReplyProbe.ref))
      productAvailabilityReplyProbe.expectMessage(ProductAvailabilityReply(Style, Color, Size, 1))
    }
  }
}
