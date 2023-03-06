/*
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
      memberTypes: Seq[api.MemberType] = Seq(api.MemberType.General)
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
      memberTypes
    )
  }

  "The Member" should {

    "allow being registered" in {
      val testKit    = MemberTestKit(new Member(_))
      val memberId   = UUID.randomUUID().toString()
      val now        = Instant.now().toEpochMilli
      val memberInfo = createMemberInfo()
      val command = api.RegisterMember(
        Some(api.MemberMap(Some(api.MemberId(memberId)), Some(memberInfo))),
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
      val currentState = testKit.currentState 
      assert(currentState.memberId.get.memberId == memberId)
      assert(currentState.memberMetaInfo.get.memberState == api.MemberState.Active)
      assert(currentState.memberInfo.get == createMemberInfo())
      
    }

    "fail registering a member with all data missing" in {
      val testKit = MemberTestKit(new Member(_))
      // val memberId = UUID.randomUUID().toString()
      val command = api.RegisterMember(None, None)
      val result  = testKit.registerMember(command)
      result.isError shouldBe true
      result.errorDescription shouldBe "Missing Member Id, Member To Add is Empty"
    }

    "fail due to missing both phone and email" in {
      val testKit    = MemberTestKit(new Member(_))
      val memberId   = UUID.randomUUID().toString()
      val memberInfo = createMemberInfo(mobileNumber = None, email = None)
      val command = api.RegisterMember(
        Some(api.MemberMap(Some(api.MemberId(memberId)), Some(memberInfo))),
        Some(api.MemberId(memberId))
      )
      val result = testKit.registerMember(command)
      result.isError shouldBe true
      result.errorDescription shouldBe "Must have at least on of Email, Mobile Phone Number"
    }

    "allow inactivation and reactivation" in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = Some(api.MemberId(UUID.randomUUID().toString()))
      val firstNow =  Instant.now().toEpochMilli
      val memberInfo = Some(createMemberInfo())
      val command = api.RegisterMember(
        Some(api.MemberMap(memberId, memberInfo)),
        memberId
        )

        val result = testKit.registerMember(command)
        result.events.isEmpty shouldBe false
        result.isError shouldBe false
        val retEvt = result.nextEvent[api.MemberRegistered]
        assert(retEvt.memberMetaInfo.get.createdOn >= firstNow)
        assert(retEvt.memberMetaInfo.get.memberState == api.MemberState.Active)
      
      val inactivateCommand = api.InactivateMember(memberId, memberId)
      val inactivateResult = testKit.inactivateMember(inactivateCommand)
      inactivateResult.events.isEmpty shouldBe false
      inactivateResult.isError shouldBe false
      val inactivateEvt = inactivateResult.nextEvent[api.MemberInactivated]
     
      assert(inactivateEvt.memberMeta.get.memberState == api.MemberState.Inactive)
      val activateCommand = api.ActivateMember(memberId, memberId)
      val activateResult = testKit.activateMember(activateCommand)
      activateResult.events.isEmpty shouldBe false
      activateResult.isError shouldBe false
      val activateEvt = activateResult.nextEvent[api.MemberActivated]
      assert(activateEvt.memberMeta.get.memberState == api.MemberState.Active)
      
    }

    "should return the registered member" in {
       val testKit = MemberTestKit(new Member(_))
      val memberId = Some(api.MemberId(UUID.randomUUID().toString()))
      val firstNow =  Instant.now().toEpochMilli
      val memberInfo = Some(createMemberInfo())
      val command = api.RegisterMember(
        Some(api.MemberMap(memberId, memberInfo)),
        memberId
        )

        val result = testKit.registerMember(command)
        result.events.isEmpty shouldBe false
        result.isError shouldBe false
        val retEvt = result.nextEvent[api.MemberRegistered]
        assert(retEvt.memberMetaInfo.get.createdOn >= firstNow)
        assert(retEvt.memberMetaInfo.get.memberState == api.MemberState.Active)

      val retCommand = api.GetMemberInfo(memberId)
      val retResult = testKit.getMemberInfo(retCommand)
      assert(retResult.reply.memberId == memberId)
      assert(retResult.reply.memberInfo == retEvt.memberInfo)


    }

    "should update Member Data " in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = Some(api.MemberId(UUID.randomUUID().toString()))
      val memberInfo = Some(createMemberInfo())
      val command = api.RegisterMember(
        Some(api.MemberMap(memberId, memberInfo)),
        memberId
      )

      val result = testKit.registerMember(command)
      result.events.isEmpty shouldBe false
      result.isError shouldBe false
      val retEvt = result.nextEvent[api.MemberRegistered]
      assert(retEvt.memberInfo == memberInfo)

      val newFirstName = "new First Name"
      val newLastName = "new Last Name"

      val updatedMemberInfo = memberInfo.map(_.copy(firstName = newFirstName, lastName = newLastName))

      val updCommand = api.UpdateMemberInfo(Some(api.MemberMap(memberId, updatedMemberInfo)), memberId)
      val updResult = testKit.updateMemberInfo(updCommand)
      updResult.events.isEmpty shouldBe false
      updResult.isError shouldBe false

      val updMemberInfo = updResult.nextEvent[api.MemberInfoUpdated]
      assert(updMemberInfo.memberInfo.get.firstName == newFirstName)
      assert(updMemberInfo.memberInfo.get.lastName == newLastName)
      assert(updMemberInfo.memberInfo.get.handle == memberInfo.get.handle)
    }

    "should fail to update a Member with bad data" in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = Some(api.MemberId(UUID.randomUUID().toString()))
      val memberInfo = Some(createMemberInfo())
      val command = api.RegisterMember(
        Some(api.MemberMap(memberId, memberInfo)),
        memberId
      )

      val result = testKit.registerMember(command)
      result.events.isEmpty shouldBe false
      result.isError shouldBe false
      val retEvt = result.nextEvent[api.MemberRegistered]
      assert(retEvt.memberInfo == memberInfo)

      
      val updatedMemberInfo = memberInfo.map(_.copy(firstName = ""))
      val updCommand = api.UpdateMemberInfo(Some(api.MemberMap(memberId, updatedMemberInfo)), memberId)
      val updResult = testKit.updateMemberInfo(updCommand)
      updResult.events.isEmpty shouldBe true
      updResult.isError shouldBe true
      updResult.errorDescription shouldBe "firstName is empty"

    }

    "should fail to transition out of Terminated" in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = Some(api.MemberId(UUID.randomUUID().toString()))
      val memberInfo = Some(createMemberInfo())
      val command = api.RegisterMember(
        Some(api.MemberMap(memberId, memberInfo)), memberId
      )
      testKit.registerMember(command)

      val terminateCommand = api.TerminateMember(memberId, memberId)
      val retEvt = testKit.terminateMember(terminateCommand)
      retEvt.events.isEmpty shouldBe false
      retEvt.isError shouldBe false
      val termRes = retEvt.reply
      termRes.memberMeta.get.memberState shouldBe api.MemberState.Terminated

      val actCommand = api.ActivateMember(memberId, memberId)
      val actEvt = testKit.activateMember(actCommand)
      actEvt.isError shouldBe true

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
*/
