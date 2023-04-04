package com.improving.app.organization.repository

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.improving.app.organization.OrganizationEvent.Empty
import com.improving.app.organization._
import com.improving.app.organization.domain.Organization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class OrganizationByOwnerProjectionHandler(tag: String, system: ActorSystem[_], repo: OrganizationRepositoryImpl)
    extends Handler[EventEnvelope[OrganizationEvent]]() {

  private val log = LoggerFactory.getLogger(getClass)

  implicit private val ec: ExecutionContext =
    system.executionContext

  override def process(envelope: EventEnvelope[OrganizationEvent]): Future[Done] = {
    envelope.event match {
      case Empty => Future.successful(Done)
      case OrganizationEstablished(Some(orgId), info, parent, members, owners, contacts, actingMember, _) =>
        log.info("OrganizationEstablished")
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
          .sequence(owners.map(ownerId => {
            repo.updateOrganizationByOwner(orgId.id, ownerId.id, organization)
          }))
          .map(_ => Done)
      case MembersAddedToOrganization(Some(orgId), newMembers, actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  members = organization.members ++ newMembers,
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      case MembersRemovedFromOrganization(Some(orgId), removedMembers, actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  members = organization.members.filterNot(removedMembers.contains(_)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      case OwnersAddedToOrganization(Some(orgId), newOwners, actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- newOwners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  owners = organization.owners ++ newOwners,
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      case OwnersRemovedFromOrganization(Some(orgId), removedOwners, actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- removedOwners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  owners = organization.owners.filterNot(removedOwners.contains(_)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      case OrganizationInfoEdited(Some(orgId), Some(info), actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  info = organization.info.map(updateInfo(_, info)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      case OrganizationActivated(Some(orgId), actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  meta = Some(
                    updateMetaInfo(
                      organization.getMeta,
                      actingMember,
                      Some(OrganizationStatus.ORGANIZATION_STATUS_ACTIVE)
                    )
                  )
                )
              )
            })
          })
          .map(_ => Done)
      case OrganizationSuspended(Some(orgId), actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  meta = Some(
                    updateMetaInfo(
                      organization.getMeta,
                      actingMember,
                      Some(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED)
                    )
                  )
                )
              )
            })
          })
          .map(_ => Done)
      case OrganizationTerminated(Some(orgId), _, _) =>
        repo.deleteOrganizationByOwnerByOrgId(orgId.id)
      case ParentUpdated(Some(orgId), Some(newParent), actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  parent = Some(newParent),
                  meta = Some(
                    updateMetaInfo(
                      organization.getMeta,
                      actingMember
                    )
                  )
                )
              )
            })
          })
          .map(_ => Done)
      case OrganizationContactsUpdated(Some(orgId), contacts, actingMember, _) =>
        repo
          .getOrganizationsByOwnerByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              owner <- organization.owners
            } yield {
              repo.updateOrganizationByOwner(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                owner.id,
                organization.copy(
                  contacts = contacts,
                  meta = Some(
                    updateMetaInfo(
                      organization.getMeta,
                      actingMember
                    )
                  )
                )
              )
            })
          })
          .map(_ => Done)
      case other =>
        throw new IllegalArgumentException(
          s"OrganizationByOwnerProjectionHandler: unknown event - $other is not valid."
        )
    }
  }
}
