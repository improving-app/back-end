package com.inventory

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import akka.serialization.SerializationExtension
import org.slf4j.LoggerFactory
import slick.dbio.DBIO
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName
import com.inventory.events.v1.{ItemAdded, ItemRemoved}
import com.inventory.publishing.v1._

/**
 * Google pubsub event publisher.
 * Note: tried alpakka and it was bound to an older version of akka and broke everything, NOT WORTH IT.
 * This class should be able to handle all versions of the backend domain as well as the protobuf messages.
 */
class ProductAvailabilityPublisher(implicit system: ActorSystem[_]) extends SlickHandler[EventEnvelope[Any]] {

  private val logger = LoggerFactory.getLogger(getClass)

  val ProjectId = "" // todo
  val TopicId = "" // todo

  val serialization = SerializationExtension(system)
  var publisher: Publisher = null

  override def process(envelope: EventEnvelope[Any]): DBIO[Done] = {
    logger.info(s"publishing $envelope")

    envelope.event match {
      case ItemAdded(entityId, style, color, size, onHandQuantity) =>
        publishItemAdded(
          ItemAddedExternal.defaultInstance
            .withEntityId(entityId)
            .withStyle(style)
            .withColor(color)
            .withSize(size)
            .withOnHandQuantity(onHandQuantity)
            .withEventType("ItemAdded")
        )
      case ItemRemoved(entityId, style, color, size, onHandQuantity) =>
        publishItemRemoved(
          ItemRemovedExternal.defaultInstance
            .withEntityId(entityId)
            .withStyle(style)
            .withColor(color)
            .withSize(size)
            .withOnHandQuantity(onHandQuantity)
            .withEventType("ItemRemoved")
        )
    }
  }

  private def publishItemAdded(itemAdded: ItemAddedExternal) =
    publish(itemAdded.toByteString)

  private def publishItemRemoved(itemRemoved: ItemRemovedExternal) =
    publish(itemRemoved.toByteString)

  private def publish(byteString: ByteString) =
    try {
      val topicName = TopicName.of(ProjectId, TopicId)
      publisher = Publisher.newBuilder(topicName).build()
      publisher.publish(PubsubMessage.newBuilder().setData(byteString).build())
      DBIO.successful(Done)
    }
    catch {
      case ex: Exception =>
        DBIO.failed(ex)
    }
    finally {
      if (publisher != null)
        publisher.shutdown()
    }
}
