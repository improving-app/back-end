package com.improving.app.organization.repository

import akka.actor.typed.ActorSystem
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import OrganizationRepositoryImpl._

object CreateTableTestUtils {

  def createTables(system: ActorSystem[_]): Unit = {
    import org.slf4j.LoggerFactory

    import scala.concurrent.Await
    import scala.concurrent.duration._

    // ok to block here, main thread
    Await.result(CassandraProjection.createTablesIfNotExists()(system), 30.seconds)

    // use same keyspace for the item_popularity table as the offset store
    val keyspace = system.settings.config
      .getString("akka.projection.cassandra.offset-store.keyspace")
    val session =
      CassandraSessionRegistry(system).sessionFor("akka.persistence.cassandra")

    Await.result(
      session.executeDDL(s"""CREATE TABLE IF NOT EXISTS $keyspace.$organizationsAndOwnersTable (
        org_id text,
        owner_id text,
        organization text,
        PRIMARY KEY (org_id, owner_id));
      """),
      30.seconds
    )

    Await.result(
      session.executeDDL(s"""CREATE TABLE IF NOT EXISTS $keyspace.$organizationsAndMembersTable (
        org_id text,
        member_id text,
        organization text,
        PRIMARY KEY (org_id, member_id));
      """),
      30.seconds
    )

    LoggerFactory
      .getLogger("Organization.CreateTableTestUtils")
      .info("Created keyspace [{}] and tables", keyspace)
  }
}
