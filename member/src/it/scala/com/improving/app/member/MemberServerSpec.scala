package com.improving.app.member

import akka.grpc.GrpcClientSettings
import com.dimafeng.testcontainers.DockerComposeContainer
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.member.api.{MemberService, MemberServiceClient}
import com.improving.app.member.domain.MemberState._
import com.improving.app.member.domain.NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
import com.improving.app.member.domain._
import org.scalatest.Assertion
import org.scalatest.Inside.inside
import org.scalatest.tagobjects.Retryable

import scala.util.Random

class MemberServerSpec extends ServiceTestContainerSpec(8081, "member-service") {

  private def getClient(containers: Containers): MemberService = {
    val (host, port) = getContainerHostPort(containers)
    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    MemberServiceClient(clientSettings)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  val memberInfo: MemberInfo = MemberInfo(
    handle = "SomeHandle",
    firstName = "FirstName",
    lastName = "LastName",
    notificationPreference = Some(NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL),
    contact = Some(
      Contact(
        firstName = "FirstName",
        lastName = "LastName",
        emailAddress = Some("someone@somewhere.com"),
        phone = None,
        userName = "SomeUserName",
      )
    ),
    organizationMembership = Seq(OrganizationId("SomeOrganization")),
    tenant = Some(TenantId("SomeTenant"))
  )

  protected val editableInfo: EditableInfo = EditableInfo(
    contact = Some(
      Contact(
        firstName = memberInfo.firstName,
        lastName = memberInfo.lastName,
        emailAddress = memberInfo.contact.flatMap(_.emailAddress),
        phone = Some("123-456-7890"),
        userName = memberInfo.contact.getOrElse(Contact.defaultInstance).userName
      )
    )
  )

  def validateMember(
      memberId: MemberId,
      memberInfo: Option[MemberInfo],
      createdBy: Option[MemberId],
      lastUpdatedBy: Option[MemberId],
      memberStatus: MemberState,
      client: MemberService
  ): Assertion = {
    val response = client.getMemberInfo(GetMemberInfo(Some(memberId))).futureValue
    response.memberId should equal(memberId)
    response.memberInfo should equal(memberInfo)
    response.memberMetaInfo should matchPattern {
      case Some(MemberMetaInfo(_, createdByMem, _, lastUpMem, memberSt, _))
          if createdByMem == createdBy && lastUpMem == lastUpdatedBy && memberSt == memberStatus =>
    }
  }

  behavior.of("MemberServer in a test container")

  it should s"expose a port for member-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "handle grpc calls for Get Member Info without Register" taggedAs Retryable in {
    withContainers { containers =>
      val memberId = Random.nextString(31)
      val response = getClient(containers)
        .getMemberInfo(
          GetMemberInfo(
            Some(MemberId(memberId))
          )
        )
        .futureValue

      response.memberId should equal(Some(Some(MemberId(memberId))))
      response.memberInfo should equal(None)
      response.memberMetaInfo should matchPattern { case None => }
    }
  }

  it should "handle grpc calls for Register Member" in {
    withContainers { containers =>
      val client = getClient(containers)
      val memberId = Random.nextString(31)
      val response = client
        .registerMember(
          RegisterMember(
            Some(MemberId(memberId)),
            Some(editableInfo),
            Some(MemberId("ADMIN_1"))
          )
        )
        .futureValue

      inside(response.memberInfo) {
        case Some(
              EditableInfo(
                Some(handle),
                Some(avatarUrl),
                Some(firstName),
                Some(lastName),
                Some(notificationPreference),
                Some(contact),
                orgs,
                Some(tenant),
                _
              )
            ) =>
          handle should equal("SomeHandle")
          avatarUrl should equal("")
          firstName should equal("FirstName")
          lastName should equal("LastName")
          inside(contact) { case Contact(fName, lName, email, phone, userName, _) =>
            fName should equal("FirstName")
            lName should equal("LastName")
            email should equal(Some("someone@somewhere.com"))
            phone should equal(None)
            userName should equal("SomeUserName")
          }
          notificationPreference should equal(Some(NOTIFICATION_PREFERENCE_EMAIL))
          orgs shouldEqual Seq(OrganizationId("SomeOrganization"))
          tenant should matchPattern { case Some(TenantId("SomeTenant", _)) => }
      }
      response.memberId should matchPattern { case Some(MemberId(_, _)) => }
      validateMember(
        MemberId(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_1")),
        MEMBER_STATE_DRAFT,
        client
      )
    }
  }

  it should "handle grpc calls for Activate Member" in {
    withContainers { containers =>
      val client = getClient(containers)
      val memberId = Random.nextString(31)
      client
        .registerMember(
          RegisterMember(
            Some(MemberId(memberId)),
            Some(editableInfo),
            Some(MemberId("ADMIN_1"))
          )
        )
        .futureValue
      val response =
        client.activateMember(ActivateMember(Some(MemberId(memberId)), Some(MemberId("ADMIN_2")))).futureValue
      response.memberId.map(_.id) should equal(Some(memberId))

      validateMember(
        MemberId(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_2")),
        MEMBER_STATE_ACTIVE,
        client
      )
    }
  }

  it should "handle grpc calls for Edit Member Info" in {
    withContainers { containers =>
      val client = getClient(containers)
      val memberId = Random.nextString(31)

      client
        .registerMember(
          RegisterMember(
            Some(MemberId(memberId)),
            Some(editableInfo),
            Some(MemberId("ADMIN_1"))
          )
        )
        .futureValue

      val response2 =
        client.activateMember(ActivateMember(Some(MemberId(memberId)), Some(MemberId("ADMIN_2")))).futureValue
      response2.memberId should equal(Some(MemberId(memberId)))

      val response =
        client
          .editMemberInfo(
            EditMemberInfo(
              Some(MemberId(memberId)),
              Some(editableInfo),
              Some(MemberId("ADMIN_5"))
            )
          )
          .futureValue
      response.memberId should equal(Some(Some(MemberId(memberId))))
      validateMember(
        MemberId(memberId),
        Some(
          memberInfo.copy(
            contact = memberInfo.contact.map(
              _.copy(
                phone = Some("123-456-7890")
              )
            )
          )
        ),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_5")),
        MEMBER_STATE_ACTIVE,
        client
      )
    }
  }

  it should "handle grpc calls for Suspend Member" in {
    withContainers { containers =>
      val client = getClient(containers)

      val memberId = Random.nextString(31)
      client
        .registerMember(
          RegisterMember(
            Some(MemberId(memberId)),
            Some(editableInfo),
            Some(MemberId("ADMIN_1"))
          )
        )
        .futureValue

      client.activateMember(ActivateMember(Some(MemberId(memberId)), Some(MemberId("ADMIN_2")))).futureValue
      val response =
        client.suspendMember(SuspendMember(Some(MemberId(memberId)), Some(MemberId("ADMIN_4")))).futureValue
      response.memberId.map(_.id) should equal(Some(memberId))
      validateMember(
        MemberId(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_4")),
        MEMBER_STATE_SUSPENDED,
        client
      )
    }
  }

  it should "handle grpc calls for Terminate Member" in {
    withContainers { containers =>
      val client = getClient(containers)

      val memberId = Random.nextString(31)

      client
        .registerMember(
          RegisterMember(
            Some(MemberId(memberId)),
            Some(editableInfo),
            Some(MemberId("ADMIN_1"))
          )
        )
        .futureValue

      client.activateMember(ActivateMember(Some(MemberId(memberId)), Some(MemberId("ADMIN_2")))).futureValue

      val response =
        client.terminateMember(TerminateMember(Some(MemberId(memberId)), Some(MemberId("ADMIN_6")))).futureValue
      response.memberId should equal(Some(MemberId(memberId)))
    }
  }
}
