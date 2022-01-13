//package com.example.inventory
//
//import akka.Done
//import akka.projection.eventsourced.EventEnvelope
//import akka.projection.slick.SlickHandler
//import org.slf4j.LoggerFactory
//import slick.dbio.DBIO
//import com.google.cloud.pubsub.v1.Publisher
//import com.google.pubsub.v1.PubsubMessage
//import com.google.pubsub.v1.TopicName
//import domain._
//
//class ProductAvailabilityPublisher() extends SlickHandler[EventEnvelope[internal.Event]] {
//
//  private val logger = LoggerFactory.getLogger(getClass)
//
//  val ProjectId = "nike"
//  val TopicId = "nike-inventory-events"
//
//  override def process(envelope: EventEnvelope[internal.Event]): DBIO[Done] = {
//    logger.debug(s"publishing ProductAvailability:ItemAdded with sku of ${envelope.event.sku}")
//    val topicName = TopicName.of(ProjectId, TopicId)
//
//    envelope.event match {
//      case event@(internal.ItemAdded(_, _) | internal.ItemRemoved(_, _)) =>
//        try {
//          // Create a publisher instance with default settings bound to the topic
//          val publisher = Publisher.newBuilder(topicName).build()
//
//          // Translate domain event to protobuf
//          val proto = ItemAdded.defaultInstance
//            .withSku(event.sku)
//            .withOnHandQuantity(event.onHandQuantity)
//
//          publisher.publish(PubsubMessage.newBuilder().setData(proto.toByteString).build())
//          DBIO.successful(Done)
//        }
//        catch {
//          case ex: Exception =>
//            DBIO.failed(ex)
//        }
//    }
//  }
//}
