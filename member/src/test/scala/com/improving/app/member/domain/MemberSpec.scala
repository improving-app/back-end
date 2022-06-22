package com.improving.app.member.domain

import com.improving.app.member.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.testkit.EventSourcedResult
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.util.UUID
import com.improving.app.organization.api.OrganizationId
import java.time.Instant

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class MemberSpec extends AnyWordSpec with Matchers {
  "The Member" should {

    "allow being registered" in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = UUID.randomUUID().toString()
      val now = Instant.now().toEpochMilli
      val memberInfo = api.MemberInfo(
        handle = "fred",
        avatarUrl = "",
        firstName = "fred",
        lastName = "george",
        mobileNumber = None,
        email = Some("someguy@someschool.com"),
        notificationPreference = api.NotificationPreference.Email,
        optIn = true,
        organizations = Seq(OrganizationId("a random organization")),
        relatedMembers = "",
        memberType = api.MemberType.Alumni

      )
      val command = api.RegisterMember(Some(api.MemberToAdd(Some(api.MemberId( memberId)), 
      Some(memberInfo))), Some(api.MemberId(memberId)))
      val result = testKit.registerMember(command)
      result.events.isEmpty shouldBe false
      result.didEmitEvents shouldBe true
      result.isError shouldBe false
      val actualEvent = result.nextEvent[api.MemberRegistered]
      actualEvent.memberId.get.memberId shouldBe memberId
      actualEvent.memberMetaInfo.get.createdBy.get.memberId shouldBe memberId
      actualEvent.memberInfo.get shouldBe memberInfo
      assert (actualEvent.memberMetaInfo.get.createdOn >= now)
    }

    "fail registering a member with all data missing" in {
      val testKit = MemberTestKit(new Member(_))
      //val memberId = UUID.randomUUID().toString()
      val command = api.RegisterMember(None, None)
      val result = testKit.registerMember(command)
      result.isError shouldBe true
      result.errorDescription shouldBe "Member To Add is Empty, Invalid Registering Member" 
    }

    "fail due to missing both phone and email" in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = UUID.randomUUID().toString()
      val memberInfo = api.MemberInfo(
        handle = "fred",
        avatarUrl = "",
        firstName = "firstName",
        lastName = "lastName",
        mobileNumber = None,
        email = None,
        notificationPreference = api.NotificationPreference.Email,
        optIn = true,
        organizations = Seq(OrganizationId("thing")),
        relatedMembers = "",
        memberType = api.MemberType.FacutlyStaff
      )
      val command = api.RegisterMember(Some(api.MemberToAdd(Some(api.MemberId(memberId)), Some(memberInfo))), Some(api.MemberId(memberId)))
      val result = testKit.registerMember(command)
      result.isError shouldBe true
      result.errorDescription shouldBe "Must have at least on of Email, Mobile Phone Number"
    }

  }
}
