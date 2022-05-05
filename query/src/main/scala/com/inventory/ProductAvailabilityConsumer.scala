package com.inventory

import akka.actor.typed.ActorSystem
import akka.serialization.SerializationExtension
import org.slf4j.LoggerFactory
import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.ProjectSubscriptionName
import com.inventory.domain.v1.BaseEvent

/**
 * Google pubsub event consumer.
 */
class ProductAvailabilityConsumer(repository: LowInventoryRepository)(implicit system: ActorSystem[_]) {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val ec = system.executionContext

  val ProjectId = "reference-applications" // todo: pull from config, which derives from secret
  val SubscriptionId = "inventory-query"
  val LowInventoryThreshold = 3

  //val serialization = SerializationExtension(system)
  var subscriber: Subscriber = null

  val subscriptionName: ProjectSubscriptionName = ProjectSubscriptionName.of(ProjectId, SubscriptionId)

  val receiver: MessageReceiver = (message: PubsubMessage, consumer: AckReplyConsumer) =>
    try {
      logger.info("Received message from pubsub")
      val event = BaseEvent.parseFrom(message.getData.toByteArray)

      if (event.onHandQuantity <= LowInventoryThreshold) {
        logger.info("Low inventory condition")
        repository.save(LowInventory(event.entityId, event.style, event.color, event.size, event.onHandQuantity))
        consumer.ack()
      } else {
        logger.info("Normal inventory condition")
        repository.delete(event.entityId)
        consumer.ack()
      }
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error consuming message from pubsub:${e.getMessage}")
    }

  import com.google.cloud.pubsub.v1.Subscriber

  try {
    subscriber = Subscriber.newBuilder(subscriptionName, receiver).build
    subscriber.startAsync.awaitRunning
    subscriber.awaitTerminated()
  }
  catch {
    case _: Throwable =>
      // Shut down the subscriber after 30s. Stop receiving messages.
      subscriber.stopAsync()
  }
}
