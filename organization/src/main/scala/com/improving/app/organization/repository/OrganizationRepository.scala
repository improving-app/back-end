package com.improving.app.organization.repository

import scala.concurrent.Future
import akka.Done

import scala.concurrent.ExecutionContext
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import com.datastax.oss.driver.api.core.cql.BatchType
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder
import com.improving.app.organization.Organization
import org.slf4j.LoggerFactory
import scalapb.json4s.JsonFormat

trait OrganizationRepository {
  def updateOrganizationByMember(orgId: String, memberId: String, organization: Organization): Future[Done]
  def deleteOrganizationByMember(orgId: String, memberId: String): Future[Done]
  def deleteOrganizationByMemberByOrgId(orgId: String): Future[Done]
  def updateOrganizationByOwner(orgId: String, ownerId: String, organization: Organization): Future[Done]
  def deleteOrganizationByOwner(orgId: String, ownerId: String): Future[Done]
  def deleteOrganizationByOwnerByOrgId(orgId: String): Future[Done]
  def getOrganizationsByMember(memberId: String): Future[Seq[Organization]]
  def getOrganizationsByMemberByOrgId(orgId: String): Future[Seq[Organization]]
  def getOrganizationsByOwner(ownerId: String): Future[Seq[Organization]]
  def getOrganizationsByOwnerByOrgId(orgId: String): Future[Seq[Organization]]

}

object OrganizationRepositoryImpl {
  val organizationsAndOwnersTable = "organization_and_owner"
  val organizationsAndMembersTable = "organization_and_member"
  val organizationToMembersTable = "organization_to_member"
  val organizationToOwnersTable = "organization_to_owner"
  val ownerToOrganizationsTable = "owner_to_organization"
  val memberToOrganizationsTable = "member_to_organization"
}

class OrganizationRepositoryImpl(session: CassandraSession, keyspace: String)(implicit val ec: ExecutionContext)
    extends OrganizationRepository {
  import OrganizationRepositoryImpl._

  private val log = LoggerFactory.getLogger(getClass)

  log.info("OrganizationRepositoryImpl - initialized")

  override def updateOrganizationByOwner(orgId: String, ownerId: String, organization: Organization): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl updateOrganizationByOwner orgId - $orgId ownerId - $ownerId")
    val bb = new BatchStatementBuilder(BatchType.UNLOGGED)
    val insertOrgAndMember = session.prepare(s"""
       INSERT INTO $keyspace.$organizationsAndOwnersTable (org_id, owner_id, organization) values (?, ?, ?);
    """)
    val insertOrgToMember = session.prepare(s"""
      INSERT INTO $keyspace.$organizationToOwnersTable (org_id, owner_id, organization) values (?, ?, ?);
    """)
    val insertMemberToOrg = session.prepare(s"""
      INSERT INTO $keyspace.$ownerToOrganizationsTable (org_id, owner_id, organization) values (?, ?, ?);
    """)
    for {
      oam <- insertOrgAndMember
      otm <- insertOrgToMember
      mto <- insertMemberToOrg
    } yield {
      val bound1 = oam.bind(
        orgId,
        ownerId,
        JsonFormat.toJsonString(organization)
      )
      val bound2 = otm.bind(
        orgId,
        ownerId,
        JsonFormat.toJsonString(organization)
      )
      val bound3 = mto.bind(
        orgId,
        ownerId,
        JsonFormat.toJsonString(organization)
      )
      val batch = bb.addStatements(bound1, bound2, bound3).build()
      session.executeWriteBatch(batch)
    }
  }.flatten

  override def updateOrganizationByMember(orgId: String, memberId: String, organization: Organization): Future[Done] = {
    log.info(
      s"OrganizationRepositoryImpl updateOrganizationByMember orgId - $orgId memberId - $memberId organization - ${JsonFormat
        .toJsonString(organization)}"
    )
    val bb = new BatchStatementBuilder(BatchType.UNLOGGED)
    val insertOrgAndMember = session.prepare(s"""
       INSERT INTO $keyspace.$organizationsAndMembersTable (org_id, member_id, organization) values (?, ?, ?);
    """)
    val insertOrgToMember = session.prepare(s"""
      INSERT INTO $keyspace.$organizationToMembersTable (org_id, member_id, organization) values (?, ?, ?);
    """)
    val insertMemberToOrg = session.prepare(s"""
      INSERT INTO $keyspace.$memberToOrganizationsTable (org_id, member_id, organization) values (?, ?, ?);
    """)
    for {
      oam <- insertOrgAndMember
      otm <- insertOrgToMember
      mto <- insertMemberToOrg
    } yield {
      val bound1 = oam.bind(
        orgId,
        memberId,
        JsonFormat.toJsonString(organization)
      )
      val bound2 = otm.bind(
        orgId,
        memberId,
        JsonFormat.toJsonString(organization)
      )
      val bound3 = mto.bind(
        orgId,
        memberId,
        JsonFormat.toJsonString(organization)
      )
      val batch = bb.addStatements(bound1, bound2, bound3).build()
      session.executeWriteBatch(batch)
    }
  }.flatten

  override def getOrganizationsByMember(memberId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl: getOrganizationsByMember $memberId")
    session
      .selectAll(
        s"SELECT organization FROM $keyspace.$memberToOrganizationsTable WHERE member_id = ? ",
        memberId
      )
      .map(rows => rows.map(row => JsonFormat.fromJsonString[Organization](row.getString("organization"))))
  }

  override def getOrganizationsByMemberByOrgId(orgId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl getOrganizationsByMemberByOrgId orgId - $orgId")
    session
      .selectAll(
        s"SELECT * FROM $keyspace.$organizationToMembersTable WHERE org_id = ? ",
        orgId
      )
      .map(rows => rows.map(row => JsonFormat.fromJsonString[Organization](row.getString("organization"))))
  }

  override def getOrganizationsByOwner(ownerId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl getOrganizationsByOwner ownerId - $ownerId")
    session
      .selectAll(
        s"SELECT organization FROM $keyspace.$ownerToOrganizationsTable WHERE owner_id = ? ",
        ownerId
      )
      .map(rows => rows.map(row => JsonFormat.fromJsonString[Organization](row.getString("organization"))))
  }

  override def getOrganizationsByOwnerByOrgId(orgId: String): Future[Seq[Organization]] = {
    log.info(s"OrganizationRepositoryImpl getOrganizationsByOwnerByOrgId orgId - $orgId")
    session
      .selectAll(
        s"SELECT organization FROM $keyspace.$organizationToOwnersTable WHERE org_id = ? ",
        orgId
      )
      .map(rows => rows.map(row => JsonFormat.fromJsonString[Organization](row.getString("organization"))))
  }

  override def deleteOrganizationByMember(orgId: String, memberId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByMember orgId - $orgId, memberId - $memberId")
    val bb = new BatchStatementBuilder(BatchType.UNLOGGED)
    val deleteOrgAndMember = session.prepare(s"""
       DELETE FROM $keyspace.$organizationsAndMembersTable WHERE org_id = ? AND member_id = ?;
    """)
    val deleteOrgToMember = session.prepare(s"""
      DELETE FROM $keyspace.$organizationToMembersTable WHERE org_id = ?;
    """)
    val deleteMemberToOrg = session.prepare(s"""
      DELETE FROM $keyspace.$memberToOrganizationsTable WHERE member_id = ?;
    """)
    for {
      oam <- deleteOrgAndMember
      otm <- deleteOrgToMember
      mto <- deleteMemberToOrg
    } yield {
      val bound1 = oam.bind(
        orgId,
        memberId
      )
      val bound2 = otm.bind(
        orgId
      )
      val bound3 = mto.bind(
        memberId
      )
      val batch = bb.addStatements(bound1, bound2, bound3).build()
      session.executeWriteBatch(batch)
    }
  }.flatten

  override def deleteOrganizationByOwner(orgId: String, ownerId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByOwner orgId - $orgId, ownerId - $ownerId")
    val bb = new BatchStatementBuilder(BatchType.UNLOGGED)
    val deleteOrgAndMember = session.prepare(s"""
       DELETE FROM $keyspace.$organizationsAndOwnersTable WHERE org_id = ? AND owner_id = ?;
    """)
    val deleteOrgToMember = session.prepare(s"""
      DELETE FROM $keyspace.$organizationToOwnersTable WHERE org_id = ?;
    """)
    val deleteMemberToOrg = session.prepare(s"""
      DELETE FROM $keyspace.$ownerToOrganizationsTable owner_id = ?;
    """)
    for {
      oam <- deleteOrgAndMember
      otm <- deleteOrgToMember
      mto <- deleteMemberToOrg
    } yield {
      val bound1 = oam.bind(
        orgId,
        ownerId
      )
      val bound2 = otm.bind(
        orgId
      )
      val bound3 = mto.bind(
        ownerId
      )
      val batch = bb.addStatements(bound1, bound2, bound3).build()
      session.executeWriteBatch(batch)
    }
  }.flatten
  import scala.jdk.CollectionConverters._
  private def executeDeleteOrganizationByMemberByOrgId(orgId: String, members: Seq[String]) = {
    log.info(s"OrganizationRepositoryImpl executeDeleteOrganizationByMemberByOrgId orgId - $orgId members $members")
    val bb = new BatchStatementBuilder(BatchType.UNLOGGED)
    val deleteOrgAndMember = session.prepare(s"""
      DELETE FROM $keyspace.$organizationsAndMembersTable WHERE org_id = ? AND member_id IN ?;
    """)
    val deleteOrgToMember = session.prepare(s"""
      DELETE FROM $keyspace.$organizationToMembersTable WHERE org_id = ?;
    """)
    val deleteMemberToOrg = session.prepare(s"""
      DELETE FROM $keyspace.$memberToOrganizationsTable WHERE member_id IN ?;
    """)
    for {
      oam <- deleteOrgAndMember
      otm <- deleteOrgToMember
      mto <- deleteMemberToOrg
    } yield {
      val bound1 = oam.bind(
        orgId,
        members.asJava
      )
      val bound2 = otm.bind(
        orgId
      )
      val bound3 = mto.bind(
        members.asJava
      )
      val batch = bb.addStatements(bound1, bound2, bound3).build()
      session.executeWriteBatch(batch)
    }
  }.flatten

  override def deleteOrganizationByMemberByOrgId(orgId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByMemberbyOrgId orgId - $orgId")
    for {
      members <- getOrganizationsByMemberByOrgId(orgId).map(seq => seq.flatMap(_.members.map(_.id)))
      done <- executeDeleteOrganizationByMemberByOrgId(orgId, members)
    } yield { done }
  }

  private def executeDeleteOrganizationByOwnerByOrgId(orgId: String, owners: Seq[String]) = {
    log.info(s"OrganizationRepositoryImpl executeDeleteOrganizationByOwnerByOrgId orgId - $orgId owners $owners")
    val bb = new BatchStatementBuilder(BatchType.UNLOGGED)
    val deleteOrgAndMember = session.prepare(s"""
       DELETE FROM $keyspace.$organizationsAndOwnersTable WHERE org_id = ? AND owner_id IN ?;
    """)
    val deleteOrgToMember = session.prepare(s"""
      DELETE FROM $keyspace.$organizationToOwnersTable WHERE org_id = ?;
    """)
    val deleteMemberToOrg = session.prepare(s"""
      DELETE FROM $keyspace.$ownerToOrganizationsTable owner_id IN ?;
    """)
    for {
      oam <- deleteOrgAndMember
      otm <- deleteOrgToMember
      mto <- deleteMemberToOrg
    } yield {
      val bound1 = oam.bind(
        orgId,
        owners.asJava
      )
      val bound2 = otm.bind(
        orgId
      )
      val bound3 = mto.bind(
        owners.asJava
      )
      val batch = bb.addStatements(bound1, bound2, bound3).build()
      session.executeWriteBatch(batch)
    }
  }.flatten

  override def deleteOrganizationByOwnerByOrgId(orgId: String): Future[Done] = {
    log.info(s"OrganizationRepositoryImpl deleteOrganizationByOwnerByOrgId orgId - $orgId")
    for {
      owners <- getOrganizationsByOwnerByOrgId(orgId).map(seq => seq.flatMap(_.members.map(_.id)))
      done <- executeDeleteOrganizationByOwnerByOrgId(orgId, owners)
    } yield {
      done
    }
  }
}
