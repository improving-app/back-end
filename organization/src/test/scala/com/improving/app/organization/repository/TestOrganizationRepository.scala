package com.improving.app.organization.repository

import akka.Done
import com.improving.app.organization._
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class TestOrganizationRepository extends OrganizationRepository {

  private val log = LoggerFactory.getLogger(getClass)

  var membersByOrgMap = scala.collection.mutable.Map[String, (String, Organization)]()
  var ownersByOrgMap = scala.collection.mutable.Map[String, (String, Organization)]()

  override def updateOrganizationByMember(
      orgId: String,
      memberId: String,
      organization: Organization
  ): Future[Done] = {
    log.info(s"updateOrganizationByMember $orgId $memberId $organization")
    membersByOrgMap = membersByOrgMap ++ scala.collection.mutable.Map(orgId -> (memberId, organization))
    Future.successful(Done)
  }

  override def deleteOrganizationByMember(orgId: String, memberId: String): Future[Done] = {
    membersByOrgMap.dropWhile { case (oid, (mid, _)) => oid == orgId && mid == memberId }
    Future.successful(Done)
  }

  override def updateOrganizationByOwner(orgId: String, ownerId: String, organization: Organization): Future[Done] = {
    ownersByOrgMap = ownersByOrgMap ++ scala.collection.mutable.Map(orgId -> (ownerId, organization))
    Future.successful(Done)
  }

  override def deleteOrganizationByOwner(orgId: String, ownerId: String): Future[Done] = {
    ownersByOrgMap.dropWhile { case (oid, (owid, _)) => oid == orgId && owid == ownerId }
    Future.successful(Done)
  }

  override def getOrganizationsByMember(memberId: String): Future[Seq[Organization]] = {
    Future.successful(
      membersByOrgMap
        .filter { case (_, (mid, _)) =>
          memberId == mid
        }
        .map { case (_, (_, organization: Organization)) => organization }
        .toSeq
    )
  }

  override def getOrganizationsByMemberByOrgId(orgId: String): Future[Seq[Organization]] = {
    Future.successful(
      membersByOrgMap
        .filter { case (oid, (_, _)) =>
          oid == orgId
        }
        .map { case (_, (_, organization: Organization)) => organization }
        .toSeq
    )
  }

  override def getOrganizationsByOwner(ownerId: String): Future[Seq[Organization]] = {
    Future.successful(
      ownersByOrgMap
        .filter { case (_, (owid, _)) =>
          owid == ownerId
        }
        .map { case (_, (_, organization: Organization)) => organization }
        .toSeq
    )
  }

  override def getOrganizationsByOwnerByOrgId(orgId: String): Future[Seq[Organization]] = {
    Future.successful(
      ownersByOrgMap
        .filter { case (oid, (_, _)) =>
          oid == orgId
        }
        .map { case (_, (_, organization: Organization)) => organization }
        .toSeq
    )
  }

//  override def deleteOrganizationByMemberByOrgId(orgId: String): Future[Done] = {
//    membersByOrgMap = membersByOrgMap.dropWhile { case (oid, (_, _)) => oid == orgId }
//    Future.successful(Done)
//  }
//
//  override def deleteOrganizationByOwnerByOrgId(orgId: String): Future[Done] = {
//    ownersByOrgMap = ownersByOrgMap.dropWhile { case (oid, (_, _)) => oid == orgId }
//    Future.successful(Done)
//  }
}
