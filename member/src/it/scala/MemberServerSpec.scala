import akka.grpc.GrpcClientSettings
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.member.domain.MemberStatus._
import com.improving.app.member.domain.NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL
import org.scalatest.Inside.inside
import com.improving.app.member.domain._
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.member.api.{MemberService, MemberServiceClient}
import com.improving.app.member.domain.GetMemberInfo

class MemberServerSpec extends ServiceTestContainerSpec(8081, "member-service") {

  private def getClient(containers: Containers): MemberService = {
    val (host, port) = getContainerHostPort(containers)
    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    MemberServiceClient(clientSettings)
  }

  val memberInfo = MemberSpec.createMemberInfo()
  var memberId: MemberId = null
  val memberInfoWithMobNumber = memberInfo.withContact(memberInfo.contact.get.withPhone("123-456-7890"))

  def validateMember(
                      memberId: Option[MemberId],
                      memberInfo: Option[MemberInfo],
                      createdBy: Option[MemberId],
                      lastUpdatedBy: Option[MemberId],
                      memberStatus: MemberStatus,
                      client: MemberService
                    ) = {
    val response = client.getMemberInfo(GetMemberInfo(memberId)).futureValue
    response.memberId should equal(memberId)
    response.memberInfo should equal(memberInfo)
    response.memberMetaInfo should matchPattern {
      case Some(MemberMetaInfo(_, createdByMem, _, lastUpMem, memberSt, _))
        if createdByMem == createdBy && lastUpMem == lastUpdatedBy && memberSt == memberStatus =>
    }
  }

  behavior of "MemberServer in a test container"

  it should s"expose a port for member-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "handle grpc calls for Get Member Info without Register" in {
    withContainers { containers =>
      val response = getClient(containers).getMemberInfo(GetMemberInfo(
        Some(MemberId("invalid-member-id"))
      )).futureValue

      response.memberId should equal(Some(MemberId("invalid-member-id")))
      response.memberInfo should equal(None)
      response.memberMetaInfo should matchPattern { case None => }
    }
  }

  it should "handle grpc calls for Register Member" in {
    withContainers { containers =>
      val client = getClient(containers)
      val response = client.registerMember(RegisterMember(
        Some(memberInfo),
        Some(MemberId("ADMIN_1"))
      )).futureValue

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
          orgs shouldEqual Seq(OrganizationId("SomeOrganization"))
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
        MEMBER_STATUS_INITIAL,
        client
      )
    }
  }

  it should "handle grpc calls for Activate Member" in {
    withContainers { containers =>
      val client = getClient(containers)
      val response = client.activateMember(ActivateMember(Some(memberId), Some(MemberId("ADMIN_2")))).futureValue
      response.memberId should equal(Some(memberId))

      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_2")),
        MEMBER_STATUS_ACTIVE,
        client
      )
    }
  }

  it should "handle grpc calls for Inactivate Member" in {
    withContainers {containers =>
      val client = getClient(containers)

      val response =
        client.inactivateMember(InactivateMember(Some(memberId), Some(MemberId("ADMIN_3")))).futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_3")),
        MEMBER_STATUS_INACTIVE,
        client
      )
    }
  }

  it should "handle grpc calls for Suspend Member" in {
    withContainers {containers =>
      val client = getClient(containers)

      val response =
        client.suspendMember(SuspendMember(Some(memberId), Some(MemberId("ADMIN_4")))).futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfo),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_4")),
        MEMBER_STATUS_SUSPENDED,
        client
      )
    }
  }

  it should "handle grpc calls for Update Member Info" in {
    withContainers {containers =>
      val client = getClient(containers)

      val response =
        client
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
        MEMBER_STATUS_SUSPENDED,
        client
      )
    }
  }

  it should "handle grpc calls for Terminate Member" in {
    withContainers {containers =>
      val client = getClient(containers)

      val response =
        client.terminateMember(TerminateMember(Some(memberId), Some(MemberId("ADMIN_6")))).futureValue
      response.memberId should equal(Some(memberId))
      validateMember(
        Some(memberId),
        Some(memberInfoWithMobNumber),
        Some(MemberId("ADMIN_1")),
        Some(MemberId("ADMIN_6")),
        MEMBER_STATUS_TERMINATED,
        client
      )
    }
  }
}