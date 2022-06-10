package com.inventory.events.v1

import com.inventory.CborSerializable

sealed trait Event extends CborSerializable {
  def entityId: String
  def style: String
  def color: String
  def size: String
  def onHandQuantity: Int
}

case class ItemAdded(
  entityId: String,
  style: String,
  color: String,
  size: String,
  onHandQuantity: Int) extends Event

case class ItemRemoved(
  entityId: String,
  style: String,
  color: String,
  size: String,
  onHandQuantity: Int) extends Event
