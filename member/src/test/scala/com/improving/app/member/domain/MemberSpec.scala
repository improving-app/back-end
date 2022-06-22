package com.improving.app.member.domain

import com.improving.app.member.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.testkit.EventSourcedResult
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.util.UUID
import com.improving.app.organization.api.OrganizationId
import java.time.Instant

// import cats.data._
// import cats.data.Validated._
import cats.implicits._

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class MemberSpec extends AnyWordSpec with Matchers {

  def createMemberInfo(
      handle: String = "fred",
      avatarUrl: String = "",
      firstName: String = "First Name",
      lastName: String = "Last Name",
      mobileNumber: Option[String] = None,
      email: Option[String] = Some("someone@somewhere.com"),
      notificationPreference: api.NotificationPreference = api.NotificationPreference.Email,
      optIn: Boolean = true,
      organizations: Seq[OrganizationId] = Seq(OrganizationId("anOrganization")),
      relatedMembers: String = "",
      memberType: api.MemberType = api.MemberType.General
  ): api.MemberInfo = {
    api.MemberInfo(
      handle,
      avatarUrl,
      firstName,
      lastName,
      mobileNumber,
      email,
      notificationPreference,
      optIn,
      organizations,
      relatedMembers,
      memberType
    )
  }

  "The Member" should {

    "allow being registered" in {
      val testKit    = MemberTestKit(new Member(_))
      val memberId   = UUID.randomUUID().toString()
      val now        = Instant.now().toEpochMilli
      val memberInfo = createMemberInfo()
      val command = api.RegisterMember(
        Some(api.MemberToAdd(Some(api.MemberId(memberId)), Some(memberInfo))),
        Some(api.MemberId(memberId))
      )
      val result = testKit.registerMember(command)
      result.events.isEmpty shouldBe false
      result.didEmitEvents shouldBe true
      result.isError shouldBe false
      val actualEvent = result.nextEvent[api.MemberRegistered]
      actualEvent.memberId.get.memberId shouldBe memberId
      actualEvent.memberMetaInfo.get.createdBy.get.memberId shouldBe memberId
      actualEvent.memberInfo.get shouldBe memberInfo
      assert(actualEvent.memberMetaInfo.get.createdOn >= now)
    }

    "fail registering a member with all data missing" in {
      val testKit = MemberTestKit(new Member(_))
      // val memberId = UUID.randomUUID().toString()
      val command = api.RegisterMember(None, None)
      val result  = testKit.registerMember(command)
      result.isError shouldBe true
      result.errorDescription shouldBe "Member To Add is Empty, Invalid Registering Member"
    }

    "fail due to missing both phone and email" in {
      val testKit    = MemberTestKit(new Member(_))
      val memberId   = UUID.randomUUID().toString()
      val memberInfo = createMemberInfo(mobileNumber = None, email = None)
      val command = api.RegisterMember(
        Some(api.MemberToAdd(Some(api.MemberId(memberId)), Some(memberInfo))),
        Some(api.MemberId(memberId))
      )
      val result = testKit.registerMember(command)
      result.isError shouldBe true
      result.errorDescription shouldBe "Must have at least on of Email, Mobile Phone Number"
    }

  }

  // to test only the validation tests, in sbt, do: testOnly *MemberSpec  -- -z "Member Validation"
  "Member Validation" should {
    "fail on no data" in {
      Member.validateMemberInfo(None) shouldBe "MemberInfo is None - cannot validate".invalidNel
    }

    "fail when no phone/email is present" in {
      Member.validateMemberInfo(
        Some(createMemberInfo(mobileNumber = None, email = None))
      ) shouldBe "Must have at least on of Email, Mobile Phone Number".invalidNel
    }

    "fail when email notification is set and no email is included" in {
      Member.validateMemberInfo(
        Some(
          createMemberInfo(
            mobileNumber = Some("some number"),
            email = None,
            notificationPreference = api.NotificationPreference.Email
          )
        )
      ) shouldBe "Notification Preference must match included data (ie SMS pref without phone number, or opposite)".invalidNel
    }

     "fail when SMS notification is set and no mobile number is included" in {
      Member.validateMemberInfo(
        Some(
          createMemberInfo(
            mobileNumber = None,
            email = Some("email@somewhere.com"),
            notificationPreference = api.NotificationPreference.SMS
          )
        )
      ) shouldBe "Notification Preference must match included data (ie SMS pref without phone number, or opposite)".invalidNel
    }

    "pass on an acceptable data set" in {
      Member.validateMemberInfo(Some(createMemberInfo())) shouldBe Some(createMemberInfo()).validNel
    }

    
  }
}
