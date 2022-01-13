//package com.nike.inventory
//
//import akka.Done
//import akka.projection.eventsourced.EventEnvelope
//import akka.projection.slick.SlickHandler
//import org.slf4j.LoggerFactory
//import slick.dbio.DBIO
//import com.nike.inventory.ProductAvailabilityEvents._
//
//import scala.concurrent.ExecutionContext
//
//class ProductAvailabilityHandler(repository: LowInventoryRepository)(implicit ec: ExecutionContext)
//  extends SlickHandler[EventEnvelope[Event]] {
//
//  private val logger = LoggerFactory.getLogger(getClass)
//
//  private val LowInventoryThreshold = 5
//
//  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = {
//    envelope.event match {
//
//      case event @ (ItemAdded(_, _) | ItemRemoved(_, _)) =>
//        logger.debug(s"ProductAvailability:ItemAdded with sku of ${event.sku}")
//        if (event.onHandQuantity <= LowInventoryThreshold) {
//          logger.debug(s"Low inventory condition for sku of ${event.sku}")
//          repository.save(LowInventory(event.sku, event.onHandQuantity))
//        }
//        else
//          DBIO.successful(Done)
//
//      case other =>
//        logger.debug(s"Unhandled event received: $other")
//        DBIO.successful(Done)
//    }
//  }
//}
