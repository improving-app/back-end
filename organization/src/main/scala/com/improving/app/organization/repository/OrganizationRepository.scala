package com.improving.app.organization.repository


import akka.Done
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import scala.annotation.tailrec
//import com.datastax.oss.driver.api.core.cql.BatchType
//import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder
import com.improving.app.organization.Organization
import org.slf4j.LoggerFactory
import scalapb.json4s.JsonFormat

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

trait OrganizationRepository {
  def updateOrganizationByMember(orgId: String, memberId: String, organization: Organization): Future[Done]
  def deleteOrganizationByMember(orgId: String, memberId: String): Future[Done]
  def deleteOrganizationByMemberByOrgId(orgId: String): Future[Done]
  def updateOrganizationByOwner(orgId: String, ownerId: String, organization: Organization): Future[Done]
  def deleteOrganizationByOwner(orgId: String, ownerId: String): Future[Done]
  def deleteOrganizationByOwnerByOrgId(orgId: String): Future[Done]
  def updateOrganizationByRoot(orgId: String, rootId: String): Future[Done]
  def getOrganizationsByMember(memberId: String): Future[Seq[Organization]]
  def getOrganizationsByMemberByOrgId(orgId: String): Future[Seq[Organization]]
  def getOrganizationsByOwner(ownerId: String): Future[Seq[Organization]]
  def getOrganizationsByOwnerByOrgId(orgId: String): Future[Seq[Organization]]
  def getRootOrganization(orgId: String): Future[String]
  def getDescendants(orgId: String): Future[Seq[String]]
  def updateOrganizationByChildren(newParentOrgId: String, childId: String): Future[Done]
  def getChildrenForOrganization(parentId: String): Future[Seq[String]]
  def deleteOrganizationByChildren(childId: String): Future[Done]

}

object OrganizationRepositoryImpl {
  val organizationsAndOwnersTable = "organization_and_owner"
  val organizationsAndMembersTable = "organization_and_member"
  val organizationsToRootTable = "organization_to_root"
  val organizationByChildrenTable = "organization_by_children"
}

class OrganizationRepositoryImpl(session: CassandraSession, keyspace: String)(implicit val ec: ExecutionContext)
    extends OrganizationRepository {
  import OrganizationRepositoryImpl._

  private val log = LoggerFactory.getLogger(getClass)

  log.info("OrganizationRepositoryImpl - initialized")

  override def updateOrganizationByOwner(orgId: String, ownerId: String, organization: Organization): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl updateOrganizationByOwner orgId - $orgId ownerId - $ownerId")
    session.executeWrite(
      s"""
          UPDATE $keyspace.$organizationsAndOwnersTable SET organization = ? WHERE org_id = ? AND owner_id = ?;
        """,
      JsonFormat.toJsonString(organization),
      orgId,
      ownerId,
    )
  }

  override def updateOrganizationByMember(orgId: String, memberId: String, organization: Organization): Future[Done] = {
    log.info(
      s"OrganizationRepositoryImpl updateOrganizationByMember orgId - $orgId memberId - $memberId organization - ${JsonFormat
        .toJsonString(organization)}"
    )
    session.executeWrite(
      s"""
          UPDATE $keyspace.$organizationsAndMembersTable SET organization = ? WHERE org_id = ? AND member_id = ?;
        """,
      JsonFormat.toJsonString(organization),
      orgId,
      memberId
    )
  }

  override def updateOrganizationByChildren(newParentOrgId: String, childId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl updateOrganizationByChildren newParentOrgId - $newParentOrgId childId - $childId")
    session.executeWrite(
      s"""
          UPDATE $keyspace.$organizationByChildrenTable SET parent_org_id = ? WHERE child_id = ?;
        """,
      newParentOrgId,
      childId
    )
  }

  override def updateOrganizationByRoot(orgId: String, rootId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl updateOrganizationByRoot orgId - $orgId rootId - $rootId")
    session.executeWrite(
      s"""
          UPDATE $keyspace.$organizationsToRootTable SET root_id = ? WHERE org_id = ?;
        """,
      rootId,
      orgId
    )
  }

  override def getOrganizationsByMember(memberId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl: getOrganizationsByMember $memberId")
    session
      .selectAll(
        s"SELECT member_id, organization FROM $keyspace.$organizationsAndMembersTable;"
      )
      .map(rows =>
        rows
          .filter(row => row.getString("member_id") == memberId)
          .map(row => JsonFormat.fromJsonString[Organization](row.getString("organization")))
          .distinct
      )
  }

  override def getOrganizationsByMemberByOrgId(orgId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl getOrganizationsByMemberByOrgId orgId - $orgId")
    session
      .selectAll(
        s"SELECT * FROM $keyspace.$organizationsAndMembersTable; "
      )
      .map(rows => {
        rows
          .filter(row => row.getString("org_id") == orgId)
          .map(row => JsonFormat.fromJsonString[Organization](row.getString("organization")))
          .distinct
      })
  }

  override def getOrganizationsByOwner(ownerId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl getOrganizationsByOwner ownerId - $ownerId")
    session
      .selectAll(
        s"SELECT owner_id, organization FROM $keyspace.$organizationsAndOwnersTable; "
      )
      .map(rows =>
        rows
          .filter(row => row.getString("owner_id") == ownerId)
          .map(row => JsonFormat.fromJsonString[Organization](row.getString("organization")))
          .distinct
      )
  }

  override def getOrganizationsByOwnerByOrgId(orgId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl getOrganizationsByOwnerByOrgId orgId - $orgId")
    session
      .selectAll(
        s"SELECT org_id, organization FROM $keyspace.$organizationsAndOwnersTable;"
      )
      .map(rows => {
        rows
          .filter(row => row.getString("org_id") == orgId)
          .map(row => JsonFormat.fromJsonString[Organization](row.getString("organization")))
          .distinct
      })
  }

  override def deleteOrganizationByMember(orgId: String, memberId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByMember orgId - $orgId, memberId - $memberId")
    executeDeleteOrganizationByMemberByOrgId(orgId, Seq(memberId))
  }

  override def deleteOrganizationByOwner(orgId: String, ownerId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByOwner orgId - $orgId, ownerId - $ownerId")
    executeDeleteOrganizationByOwnerByOrgId(orgId, Seq(ownerId))
  }

  private def executeDeleteOrganizationByMemberByOrgId(orgId: String, members: Seq[String]) = {
    log.info(s"OrganizationRepositoryImpl executeDeleteOrganizationByMemberByOrgId orgId - $orgId members $members")
    session.executeWrite(
      s"""
          DELETE FROM $keyspace.$organizationsAndMembersTable WHERE org_id = ? AND member_id IN ?;
        """,
      orgId,
      members.asJava
    )
  }

  override def deleteOrganizationByMemberByOrgId(orgId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByMemberbyOrgId orgId - $orgId")
    for {
      members <- getOrganizationsByMemberByOrgId(orgId).map(seq => seq.flatMap(_.members.map(_.id)))
      done <- executeDeleteOrganizationByMemberByOrgId(orgId, members)
    } yield { done }
  }

  private def executeDeleteOrganizationByOwnerByOrgId(orgId: String, owners: Seq[String]) = {
    log.info(s"OrganizationRepositoryImpl executeDeleteOrganizationByOwnerByOrgId orgId - $orgId owners $owners")
    session.executeWrite(
      s"""
          DELETE FROM $keyspace.$organizationsAndOwnersTable WHERE org_id = ? AND owner_id IN ?;
        """,
      orgId,
      owners.asJava
    )
  }

  override def deleteOrganizationByOwnerByOrgId(orgId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByOwnerByOrgId orgId - $orgId")
    for {
      owners <- getOrganizationsByOwnerByOrgId(orgId).map(seq => seq.flatMap(_.owners.map(_.id)))
      done <- executeDeleteOrganizationByOwnerByOrgId(orgId, owners)
    } yield {
      done
    }
  }

  override def deleteOrganizationByChildren(childId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByChildren childId - $childId")
    session.executeWrite(
      s"""
          DELETE FROM $keyspace.$organizationByChildrenTable WHERE child_id = ?;
        """,
      childId
    )
  }

  override def getRootOrganization(orgId: String): Future[String] =
  {
    log.info(s"OrganizationRepositoryImpl getRootOrganization orgId - $orgId")
    session
      .selectAll(
        s"SELECT org_id, root_id FROM $keyspace.$organizationsToRootTable;"
      )
      .map(rows => {
        rows
          .filter(row => row.getString("org_id") == orgId)
          .map(row => row.getString("root_id"))
      }.head)
  }

  override def getChildrenForOrganization(parentId: String): Future[Seq[String]] = {
    log.info(s"OrganizationRepositoryImpl getChildrenForOrganization parentId - $parentId")
    session
      .selectAll(
        s"SELECT parent_id, child_id FROM $keyspace.$organizationByChildrenTable;"
      )
      .map(rows => {
        rows
          .filter(row => row.getString("parent_id") == parentId)
          .map(row => row.getString("child_id"))
      })
  }


  //TODO: make it return the root org id
  @tailrec
  private def getDescendants(inOrgs: Seq[String], children: Future[Seq[String]]): Future[Seq[String]] = {
    if(inOrgs.isEmpty) children
    else {
      val newChildren = getChildrenForOrganization(inOrgs.head)
      val allChildren: Future[Seq[String]] = children.flatMap(c => newChildren.map(n => c ++ n))
      getDescendants(inOrgs.tail, allChildren)
    }
  }

  override def getDescendants(orgId: String): Future[Seq[String]] = {
    getDescendants(Seq(orgId), Future.successful(Seq.empty[String]))
  }

}
