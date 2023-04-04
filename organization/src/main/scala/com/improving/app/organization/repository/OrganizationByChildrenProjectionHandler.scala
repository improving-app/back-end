package com.improving.app.organization.repository

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.improving.app.organization.{OrganizationEstablished, OrganizationEvent, ParentUpdated}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class OrganizationByChildrenProjectionHandler(tag: String, system: ActorSystem[_], repo: OrganizationRepository)
  extends Handler[EventEnvelope[OrganizationEvent]]() {

  private val log = LoggerFactory.getLogger(getClass)

  ///implicit private val ec: ExecutionContext =
    //system.executionContext

  override def process(envelope: EventEnvelope[OrganizationEvent]): Future[Done] = {
    envelope.event match {
      case OrganizationEstablished(Some(orgId), _, Some(parent), _, _, _, _, _) =>
        log.info("OrganizationByMemberProjectionHandler: OrganizationEstablished")
        repo.updateOrganizationByChildren(parent.id, orgId.id)
      case ParentUpdated(Some(orgId), parent, _, _) =>
        log.info("OrganizationByMemberProjectionHandler: ParentUpdated")
        parent match {
          case None => repo.deleteOrganizationByChildren(orgId.id)
          case Some(parent) => repo.updateOrganizationByChildren(parent.id, orgId.id)
        }
      case _ => Future.successful(Done)
    }
  }

}
