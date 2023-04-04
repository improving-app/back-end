package com.improving.app.organization.repository

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.improving.app.common.domain.OrganizationId
import com.improving.app.organization.{OrganizationEstablished, OrganizationEvent, ParentUpdated}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class OrganizationToRootProjectionHandler (tag: String, system: ActorSystem[_], repo: OrganizationRepository)
  extends Handler[EventEnvelope[OrganizationEvent]]() {

  private val log = LoggerFactory.getLogger(getClass)

  implicit private val ec: ExecutionContext =
    system.executionContext

  private def getRootFromParent(parent: Option[OrganizationId], orgId: OrganizationId): Future[String] = {
    parent.map(parentId => repo.getRootOrganization(parentId.id)).getOrElse(Future.successful(orgId.id))
  }


  //If it doesn't have a parent, its root is itself
  override def process(envelope: EventEnvelope[OrganizationEvent]): Future[Done] = {
    envelope.event match {
      case OrganizationEstablished(Some(orgId), _, parent, _, _, _, _, _) =>
        log.info("OrganizationByMemberProjectionHandler: OrganizationEstablished")
        for {
          root: String <- getRootFromParent(parent, orgId)
            _ <- repo.updateOrganizationByRoot(orgId.id, root)
        } yield Done
      case ParentUpdated(Some(orgId), parent, _, _) =>
        log.info("OrganizationByMemberProjectionHandler: ParentUpdated")
        for {
          root: String <- getRootFromParent(parent, orgId)
            _ <- repo.updateOrganizationByRoot(orgId.id, root)
        } yield Done
      case _ => Future.successful(Done)
    }
  }

}
