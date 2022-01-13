package com.yoppworks.inventory.internal

sealed trait Event {
  def sku: String

  def onHandQuantity: Int
}

final case class ItemAdded(sku: String, onHandQuantity: Int) extends Event

final case class ItemRemoved(sku: String, onHandQuantity: Int) extends Event
