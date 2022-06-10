package com.inventory

import scala.concurrent.{ExecutionContext, Future}

case class LowInventory(
  entityId: String,
  style: String,
  color: String,
  size: String,
  quantity: Int) extends Serializable

trait LowInventoryRepository {
  def getAll(): Future[Seq[LowInventory]]

  def find(entityId: String): Future[Option[LowInventory]]

  def save(lowInventory: LowInventory)(implicit ec: ExecutionContext): Unit

  def delete(entityId: String)(implicit ec: ExecutionContext): Unit

  def createTable(): Future[Unit]
}
