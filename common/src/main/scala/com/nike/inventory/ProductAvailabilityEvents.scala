package com.nike.inventory

object ProductAvailabilityEvents {
  sealed trait Event extends CborSerializable {
    def sku: String
    def onHandQuantity: Int
  }

  final case class ItemAdded(sku: String, onHandQuantity: Int) extends Event
  final case class ItemRemoved(sku: String, onHandQuantity: Int) extends Event
}
