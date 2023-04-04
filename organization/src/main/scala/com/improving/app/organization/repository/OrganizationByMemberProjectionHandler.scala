package com.improving.app.organization.repository

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.organization._
import com.improving.app.organization.OrganizationEvent.Empty
import com.improving.app.organization.domain.Organization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class OrganizationByMemberProjectionHandler(tag: String, system: ActorSystem[_], repo: OrganizationRepository)
    extends Handler[EventEnvelope[OrganizationEvent]]() {

  private val log = LoggerFactory.getLogger(getClass)

  implicit private val ec: ExecutionContext =
    system.executionContext

  def handleOrgEvent(
      orgId: OrganizationId,
      members: Seq[MemberId]
  )(makeNewOrg: Organization => Organization) = repo
    .getOrganizationsByMemberByOrgId(orgId.id)
    .map(organizations => {
      Future.sequence(for {
        organization <- organizations
        member <- members
      } yield {
        repo.updateOrganizationByMember(
          organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
          member.id,
          makeNewOrg(organization)
        )
      })
    })
    .map(_ => Done)

  def handleOrgEvent(
      orgId: OrganizationId
  )(makeNewOrg: Organization => Organization) = repo
    .getOrganizationsByMemberByOrgId(orgId.id)
    .map(organizations => {
      Future.sequence(for {
        organization <- organizations
        member <- organization.members
      } yield {
        repo.updateOrganizationByMember(
          organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
          member.id,
          makeNewOrg(organization)
        )
      })
    })
    .map(_ => Done)

  override def process(envelope: EventEnvelope[OrganizationEvent]): Future[Done] = {
    envelope.event match {
      case Empty => Future.successful(Done)
      case OrganizationEstablished(Some(orgId), info, parent, members, owners, contacts, actingMember, _) =>
        log.info("OrganizationByMemberProjectionHandler: OrganizationEstablished")
        val organization = Organization(
          Some(orgId),
          info,
          parent,
          members,
          owners,
          contacts,
          Some(createMetaInfo(actingMember))
        )
        Future
          .sequence(members.map(memberId => {
            repo.updateOrganizationByMember(orgId.id, memberId.id, organization)
          }))
          .map(_ => Done)
      case MembersAddedToOrganization(Some(orgId), newMembers, actingMember, _) =>
        handleOrgEvent(orgId, newMembers) { organization =>
          organization.copy(
            members = organization.members ++ newMembers,
            meta = Some(updateMetaInfo(organization.getMeta, actingMember))
          )
        }
      case MembersRemovedFromOrganization(Some(orgId), removedMembers, actingMember, _) =>
        handleOrgEvent(orgId, removedMembers) { organization =>
          organization.copy(
            members = organization.members.filterNot(removedMembers.contains(_)),
            meta = Some(updateMetaInfo(organization.getMeta, actingMember))
          )
        }
      case OwnersAddedToOrganization(Some(orgId), newOwners, actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            owners = organization.owners ++ newOwners,
            meta = Some(updateMetaInfo(organization.getMeta, actingMember))
          )
        }
      case OwnersRemovedFromOrganization(Some(orgId), removedOwners, actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            owners = organization.owners.filterNot(removedOwners.contains(_)),
            meta = Some(updateMetaInfo(organization.getMeta, actingMember))
          )
        }
      case OrganizationInfoEdited(Some(orgId), Some(info), actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            info = organization.info.map(updateInfo(_, info)),
            meta = Some(updateMetaInfo(organization.getMeta, actingMember))
          )
        }
      case OrganizationActivated(Some(orgId), actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            meta = Some(
              updateMetaInfo(
                organization.getMeta,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_ACTIVE)
              )
            )
          )
        }
      case OrganizationSuspended(Some(orgId), actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            meta = Some(
              updateMetaInfo(
                organization.getMeta,
                actingMember,
                Some(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED)
              )
            )
          )
        }
      case OrganizationTerminated(Some(orgId), _, _) =>
        repo.deleteOrganizationByMemberByOrgId(orgId.id)
      case ParentUpdated(Some(orgId), Some(newParent), actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            parent = Some(newParent),
            meta = Some(
              updateMetaInfo(
                organization.getMeta,
                actingMember
              )
            )
          )
        }
      case OrganizationContactsUpdated(Some(orgId), contacts, actingMember, _) =>
        handleOrgEvent(orgId) { organization =>
          organization.copy(
            contacts = contacts,
            meta = Some(
              updateMetaInfo(
                organization.getMeta,
                actingMember
              )
            )
          )
        }
      case other =>
        throw new IllegalArgumentException(
          s"OrganizationByMemberProjectionHandler: unknown event - $other is not valid."
        )
    }
  }
}
