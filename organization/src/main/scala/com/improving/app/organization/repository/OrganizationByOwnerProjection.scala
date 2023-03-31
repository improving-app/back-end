package com.improving.app.organization.repository

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{AtLeastOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import com.improving.app.organization.OrganizationEvent
import com.improving.app.organization.domain.Organization

object OrganizationByOwnerProjection {

  def init(system: ActorSystem[_], repository: OrganizationRepositoryImpl): Unit = {
    ShardedDaemonProcess(system).init(
      name = "OrganizationByOwnerProjection",
      Organization.tags.size,
      index => ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   repository: OrganizationRepositoryImpl,
                                   index: Int
  ): AtLeastOnceProjection[Offset, EventEnvelope[OrganizationEvent]] = {
    val tag = Organization.tags(index)

    val sourceProvider: SourceProvider[Offset, EventEnvelope[OrganizationEvent]] =
      EventSourcedProvider.eventsByTag[OrganizationEvent](
        system = system,
        readJournalPluginId = CassandraReadJournal.Identifier,
        tag = tag
      )

    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("organizations", tag),
      sourceProvider,
      handler = () => new OrganizationByOwnerProjectionHandler(tag, system, repository)
    )
  }

}
