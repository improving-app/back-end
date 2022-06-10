package com.inventory

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContext, Future}

class SlickLowInventoryRepository(val dbConfig: DatabaseConfig[PostgresProfile]) extends LowInventoryRepository {
  import dbConfig.profile.api._

  private class LowInventoryTable(tag: Tag) extends Table[LowInventory](tag, "low_inventory") {
    def entityId = column[String]("entity_id", O.PrimaryKey)
    def style = column[String]("style")
    def color = column[String]("color")
    def size = column[String]("size")
    def quantity = column[Int]("quantity")

    override def * = (entityId, style, color, size, quantity).mapTo[LowInventory]
  }

  private val lowInventoryTable = TableQuery[LowInventoryTable]

  private def filtered(entityId: String) =
    TableQuery[LowInventoryTable].filter(l =>
      l.entityId===entityId
    )

  override def getAll(): Future[Seq[LowInventory]] =
    dbConfig.db.run(TableQuery[LowInventoryTable].result)

  override def save(lowInventory: LowInventory)(implicit ec: ExecutionContext): Unit =
    dbConfig.db.run(TableQuery[LowInventoryTable].insertOrUpdate(lowInventory))

  override def delete(entityId: String)(implicit ec: ExecutionContext): Unit =
    dbConfig.db.run(filtered(entityId).delete)

  override def createTable(): Future[Unit] =
    dbConfig.db.run(lowInventoryTable.schema.createIfNotExists)

  override def find(entityId: String): Future[Option[LowInventory]] =
    dbConfig.db.run(filtered(entityId).result.headOption)
}
