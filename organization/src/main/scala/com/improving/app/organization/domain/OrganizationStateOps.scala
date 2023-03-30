package com.improving.app.organization.domain

import com.improving.app.common.domain.{MemberId, OrganizationId}
import com.improving.app.organization._

object OrganizationStateOps {

  implicit class OrganizationStateOps(organizationState: OrganizationState) {
    def getOrgId: Option[OrganizationId] = organizationState match {
      case InitialEmptyOrganizationState(orgId, _) => orgId
      case DraftOrganizationState(orgId, _, _, _, _) => orgId
      case EstablishedOrganizationState(orgId, _, _, _, _, _, _, _) => orgId
      case TerminatedOrganizationState(orgId, _, _) => orgId
      case _ => None
    }

    def getMembers: Seq[MemberId] = organizationState match {
      case DraftOrganizationState(_, _, optionalDraftInfo, _, _) => optionalDraftInfo.map(_.members).getOrElse(Seq.empty)
      case EstablishedOrganizationState(_, _, _, members, _, _, _, _) => members
      case _ => Seq.empty
    }

    def getOwners: Seq[MemberId] = organizationState match {
      case DraftOrganizationState(_, requiredDraftInfo, _, _, _) => requiredDraftInfo.map(_.owners).getOrElse(Seq.empty)
      case EstablishedOrganizationState(_, _, _, _, owners, _, _, _) => owners
      case _ => Seq.empty
    }

    def getContacts: Seq[Contacts] = organizationState match {
      case DraftOrganizationState(_, _, optionalDraftInfo, _, _) => optionalDraftInfo.map(_.contacts).getOrElse(Seq.empty)
      case EstablishedOrganizationState(_, _, _, _, _, contacts, _, _) => contacts
      case _ => Seq.empty
    }

    def isSuspended: Boolean = organizationState match {
      case state: EstablishedOrganizationState => state.meta.map(_.currentStatus).contains(OrganizationStatus.ORGANIZATION_STATUS_SUSPENDED)
      case _ => false
    }
  }

}
