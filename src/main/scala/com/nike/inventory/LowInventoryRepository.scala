package com.nike.inventory

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContext, Future}

case class LowInventory(sku: String, quantity: Int) extends Serializable

class LowInventoryRepository(val dbConfig: DatabaseConfig[PostgresProfile]) {
  import dbConfig.profile.api._

  private class LowInventoryTable(tag: Tag) extends Table[LowInventory](tag, "low_inventory") {
    def id = column[String]("sku", O.PrimaryKey)
    def quantity = column[Int]("quantity")

    def * = (id, quantity).mapTo[LowInventory]
  }

  private val lowInventoryTable = TableQuery[LowInventoryTable]

  def save(lowInventory: LowInventory)(implicit ec: ExecutionContext) = {
    lowInventoryTable.insertOrUpdate(lowInventory).map(_ => Done)
  }

  def createTable(): Future[Unit] =
    dbConfig.db.run(lowInventoryTable.schema.createIfNotExists)
}
