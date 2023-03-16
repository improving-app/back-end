package com.improving.app.member.api

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.member.domain.MemberStatus._
import com.improving.app.member.domain.NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
import com.improving.app.member.domain._
import com.improving.app.member.utils.{CassandraTestContainer, LoanedActorSystem}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside.inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

class MemberServiceIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers
    with CassandraTestContainer
    with LoanedActorSystem {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Milliseconds)))

  implicit private val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  override val cassandraInitScriptPath: String = "member/src/test/resources/cassandra-init.cql"

  override def configOverrides: Map[String, Any] = super.configOverrides

  "MemberService" ignore {
    var memberId: MemberId = null
    val memberInfo = MemberSpec.createMemberInfo()
    val memberService = new MemberServiceImpl()(system.toTyped)
    val memberInfoWithMobNumber = memberInfo.withContact(memberInfo.contact.get.withPhone("123-456-7890"))

    def validateMember(
        memberId: Option[MemberId],
        memberInfo: Option[MemberInfo],
        createdBy: Option[MemberId],
        lastUpdatedBy: Option[MemberId],
        memberStatus: MemberStatus
    ) = {
      val response = memberService.getMemberInfo(GetMemberInfo(memberId)).futureValue
      response.memberId should equal(memberId)
      response.memberInfo should equal(memberInfo)
      response.memberMetaInfo should matchPattern {
        case Some(MemberMetaInfo(_, createdByMem, _, lastUpMem, memberSt, _))
            if createdByMem == createdBy && lastUpMem == lastUpdatedBy && memberSt == memberStatus =>
      }
    }

    "handle grpc calls for Get Member Info without Register" in {
      val response = memberService.getMemberInfo(GetMemberInfo(Some(MemberId("invalid-member-id")))).futureValue
      response.memberId should equal(Some(MemberId("invalid-member-id")))
      response.memberInfo should equal(None)
      response.memberMetaInfo should matchPattern { case None => }
    }

    "handle grpc calls for Register Member" in {

      val response =
        memberService.registerMember(RegisterMember(Some(memberInfo), Some(MemberId("ADMIN_1")))).futureValue
      inside(response.memberInfo) {
        case Some(
              MemberInfo(
                handle,
                avatarUrl,
                firstName,
                lastName,
                notPref,
                notOptIn,
                contact,
                orgs,
                tenant,
                _
              )
            ) =>
          handle should equal("SomeHandle")
          avatarUrl should equal("")
          firstName should equal("FirstName")
          lastName should equal("LastName")
          inside(contact) { case Some(Contact(fName, lName, email, phone, userName, _)) =>
            fName should equal("FirstName")
            lName should equal("LastName")
            email should equal(Some("someone@somewhere.com"))
            phone should equal(None)
            userName should equal("SomeUserName")
          }
          notPref should equal(NOTIFICATION_PREFERENCE_EMAIL)
          notOptIn should equal(true)
          orgs should matchPattern { case OrganizationId("SomeOrganization", _) :: Nil => }
          tenant should matchPattern { case Some(TenantId("SomeTenant", _)) => }
      }
      response.memberId should matchPattern { case Some(MemberId(_, _)) => }
      memberId = response.memberId.get
      println("MemberId: " + memberId)
      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_1")),
        MEMBER_STATUS_INITIAL
      )
    }

    "handle grpc calls for Activate Member" in {
      val response = memberService.activateMember(ActivateMember(Some(memberId), Some(MemberId("ADMIN_2")))).futureValue
      response.memberId should equal(Some(memberId))

      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_2")),
        MEMBER_STATUS_ACTIVE
      )
    }

    "handle grpc calls for Inactivate Member" in {
      val response =
        memberService.inactivateMember(InactivateMember(Some(memberId), Some(MemberId("ADMIN_3")))).futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_3")),
        MEMBER_STATUS_INACTIVE
      )
    }

    "handle grpc calls for Suspend Member" in {
      val response =
        memberService.suspendMember(SuspendMember(Some(memberId), Some(MemberId("ADMIN_4")))).futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_4")),
        MEMBER_STATUS_SUSPENDED
      )
    }

    "handle grpc calls for Update Member Info" in {

      val response =
        memberService
          .updateMemberInfo(
            UpdateMemberInfo(
              Some(memberId),
              Some(memberInfoWithMobNumber),
              Some(MemberId("ADMIN_5"))
            )
          )
          .futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfoWithMobNumber),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_5")),
        MEMBER_STATUS_SUSPENDED
      )
    }

    "handle grpc calls for Terminate Member" in {
      val response =
        memberService.terminateMember(TerminateMember(Some(memberId), Some(MemberId("ADMIN_6")))).futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfoWithMobNumber),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_6")),
        MEMBER_STATUS_TERMINATED
      )
    }
  }

}
