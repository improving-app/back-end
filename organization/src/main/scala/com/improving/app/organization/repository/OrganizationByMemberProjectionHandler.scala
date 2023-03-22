package com.improving.app.organization.repository

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
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

  override def process(envelope: EventEnvelope[OrganizationEvent]): Future[Done] = {
    envelope.event match {
      case Empty => Future.successful(Done)
      case OrganizationEstablished(Some(orgId), info, parent, members, owners, contacts, actingMember, _) => {
        log.info("OrganizationByMemberProjectionHandler: OrganizationEstablished")
        val organization = Organization(
          Some(orgId),
          info,
          parent,
          members,
          owners,
          contacts,
          Some(createMetaInfo(actingMember)),
          OrganizationStatus.ORGANIZATION_STATUS_DRAFT
        )
        Future
          .sequence(members.map(memberId => {
            repo.updateOrganizationByMember(orgId.id, memberId.id, organization)
          }))
          .map(_ => Done)
      }
      case MembersAddedToOrganization(Some(orgId), newMembers, actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- newMembers
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  members = organization.members ++ newMembers,
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      }
      case MembersRemovedFromOrganization(Some(orgId), removedMembers, actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- removedMembers
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  members = organization.members.filterNot(removedMembers.contains(_)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      }
      case OwnersAddedToOrganization(Some(orgId), newOwners, actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  owners = organization.owners ++ newOwners,
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      }
      case OwnersRemovedFromOrganization(Some(orgId), removedOwners, actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  owners = organization.owners.filterNot(removedOwners.contains(_)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      }
      case OrganizationInfoUpdated(Some(orgId), Some(info), actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  info = organization.info.map(updateInfo(_, info)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      }
      case OrganizationReleased(Some(orgId), _, _) => {
        repo.deleteOrganizationByMemberByOrgId(orgId.id)
      }
      case OrganizationActivated(Some(orgId), actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  status = OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
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
      }
      case OrganizationSuspended(Some(orgId), actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  status = OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED,
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
      }
      case OrganizationTerminated(Some(orgId), _, _) => {
        repo.deleteOrganizationByMemberByOrgId(orgId.id)
      }
      case ParentUpdated(Some(orgId), Some(newParent), actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
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
      }
      case OrganizationStatusUpdated(Some(orgId), newStatus, actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  status = newStatus,
                  meta = Some(
                    updateMetaInfo(
                      organization.getMeta,
                      actingMember,
                      Some(newStatus)
                    )
                  )
                )
              )
            })
          })
          .map(_ => Done)
      }
      case OrganizationContactsUpdated(Some(orgId), contacts, actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
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
      }
      case OrganizationAccountsUpdated(Some(orgId), Some(info), actingMember, _) => {
        repo
          .getOrganizationsByMemberByOrgId(orgId.id)
          .map(organizations => {
            Future.sequence(for {
              organization <- organizations
              member <- organization.members
            } yield {
              repo.updateOrganizationByMember(
                organization.orgId.map(_.id).getOrElse("OrgId is NOT FOUND."),
                member.id,
                organization.copy(
                  info = organization.info.map(updateInfo(_, info)),
                  meta = Some(updateMetaInfo(organization.getMeta, actingMember))
                )
              )
            })
          })
          .map(_ => Done)
      }
      case other =>
        throw new IllegalArgumentException(
          s"OrganizationByMemberProjectionHandler: unknown event - $other is not valid."
        )
    }
  }
}
