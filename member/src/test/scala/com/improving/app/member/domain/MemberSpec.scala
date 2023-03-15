package com.improving.app.member.domain

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.typed.{Cluster, Join}
import akka.pattern.StatusReply
import com.improving.app.common.domain.{Contact, MemberId, OrganizationId, TenantId}
import com.improving.app.member.domain.Member.{MemberCommand, MemberEntityKey}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class MemberSpec
    extends ScalaTestWithActorTestKit(MemberSpec.config)
    with AnyWordSpecLike
    with Matchers
    with StrictLogging {

  val member1Id: String = "MEMBER-1"

  val waitDuration: FiniteDuration = 5.seconds

  val sharding: ClusterSharding = ClusterSharding(system)
  //Member.initSharding(sharding)

  "The Member" should {

    ClusterSharding(system).init(
      Entity(MemberEntityKey)(entityContext => Member(entityContext.entityTypeKey.name, entityContext.entityId))
    )

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)

    val memberInfo = MemberSpec.createMemberInfo()
    "allow Member to be registered" in {

      val registerMember = RegisterMember(Some(memberInfo), Some(MemberId("ADMIN")))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(registerMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberEventResponse(
                MemberRegistered(Some(MemberId(memId)), Some(memberInfo), Some(actingMember), Some(updatedOn))
              )
            ) =>
          logger.info(s"Member Registered $memId: $memberInfo: $actingMember: $updatedOn")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "do not allow Member to be re-registed with same memberId" in {

      val registerMember = RegisterMember(Some(memberInfo), Some(MemberId("ADMIN")))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(registerMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Error(someError: Throwable) =>
          logger.error(s"Member Registered failed with error ${someError.getMessage}", someError)
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Member to be activated" in {

      val activateMember = ActivateMember(Some(MemberId(member1Id)), Some(MemberId("ADMIN")))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(activateMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberEventResponse(MemberActivated(Some(MemberId(memId)), Some(actingMember), Some(updatedOn)))
            ) =>
          logger.info(s"Member Activated $memId: : $actingMember: $updatedOn")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Member to be Inactivated" in {

      val inactivateMember = InactivateMember(Some(MemberId(member1Id)), Some(MemberId("ADMIN")))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(inactivateMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberEventResponse(MemberInactivated(Some(MemberId(memId)), Some(actingMember), Some(updatedOn)))
            ) =>
          logger.info(s"Member Inactivated $memId: $actingMember: $updatedOn")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Member to be Suspended" in {

      val suspendMember = SuspendMember(Some(MemberId(member1Id)), Some(MemberId("ADMIN")))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(suspendMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberEventResponse(MemberSuspended(Some(MemberId(memId)), Some(actingMember), Some(updatedOn)))
            ) =>
          logger.info(s"Member Suspended $memId: $actingMember: $updatedOn")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Member Info to be Updated with Phone Not set" in {

      val suspendMember = UpdateMemberInfo(
        Some(MemberId(member1Id)),
        Some(memberInfo.withNotificationPreference(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)),
        Some(MemberId("ADMIN"))
      )

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(suspendMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Error(errorMsg) =>
          logger.info(s"Member Info Update rejected with error : $errorMsg")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Member Info to be Updated with Phone No set" in {

      val suspendMember = UpdateMemberInfo(
        Some(MemberId(member1Id)),
        Some(
          memberInfo
            .withNotificationPreference(NotificationPreference.NOTIFICATION_PREFERENCE_SMS)
            .withContact(memberInfo.contact.get.withPhone("1234567890"))
        ),
        Some(MemberId("ADMIN"))
      )

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(suspendMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberEventResponse(
                MemberInfoUpdated(
                  Some(MemberId(memId)),
                  Some(memberInfo: MemberInfo),
                  Some(actingMember),
                  Some(updatedOn)
                )
              )
            ) =>
          logger.info(s"Member Info Updated $memId: $memberInfo: $actingMember: $updatedOn")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Fetching Member suspended Member info" in {

      val getMemberInfo = GetMemberInfo(Some(MemberId(member1Id)))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(getMemberInfo, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberData(Some(MemberId(memId)), Some(memberInfo: MemberInfo), Some(memberMetaInfo))
            ) =>
          logger.info(s"Member Fetched $memId: $memberInfo: $memberMetaInfo")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }

    "allow Member to be Terminated" in {

      val terminateMember = TerminateMember(Some(MemberId(member1Id)), Some(MemberId("ADMIN")))

      val p = TestProbe[StatusReply[MemberResponse]]()
      val ref: EntityRef[Member.MemberCommand] = sharding.entityRefFor(MemberEntityKey, member1Id)

      ref.ask[MemberResponse](_ => MemberCommand(terminateMember, p.ref))
      val result: Seq[StatusReply[MemberResponse]] = p.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              MemberEventResponse(MemberTerminated(Some(MemberId(memId)), Some(actingMember), Some(updatedOn)))
            ) =>
          logger.info(s"Member Terminated $memId: $actingMember $updatedOn")
          FishingOutcome.Complete
        case other =>
          logger.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
      result.length shouldBe 1
    }
  }
  /*


    "allow inactivation and reactivation" in {
      val testKit = MemberTestKit(new Member(_))
      val memberId = Some(api.MemberId(UUID.randomUUID().toString()))
      val firstNow = Instant.now().toEpochMilli
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
        Some(api.MemberMap(memberId, memberInfo)),
        memberId
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

  }*/
}

object MemberSpec {
  val config: Config = ConfigFactory.parseString("""
        akka.loglevel = INFO
        #akka.persistence.typed.log-stashing = on
        akka.actor.provider = cluster
        akka.remote.artery.canonical.port = 0
        akka.remote.artery.canonical.hostname = 127.0.0.1
//        akka.cluster.seed-nodes = [
//          "akka.tcp://yourClusterSystem@127.0.0.1:2551",
//          "akka.tcp://yourClusterSystem@127.0.0.1:2552"
//        ]
        akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
        akka.persistence.journal.inmem.test-serialization = on

        akka.actor.serialization-bindings{
        "com.improving.app.common.serialize.PBMsgSerializable" = proto
        "com.improving.app.common.serialize.PBMsgOneOfSerializable" = proto
        "com.improving.app.common.serialize.PBEnumSerializable" = proto
          }
      """)

  def createMemberInfo(
      userName: String = "SomeUserName",
      handle: String = "SomeHandle",
      avatarUrl: String = "",
      firstName: String = "FirstName",
      lastName: String = "LastName",
      phNo: Option[String] = None,
      email: Option[String] = Some("someone@somewhere.com"),
      notificationPreference: NotificationPreference = NotificationPreference.NOTIFICATION_PREFERENCE_EMAIL,
      optIn: Boolean = true,
      organizations: Seq[OrganizationId] = Seq(OrganizationId("SomeOrganization")),
      tentant: Option[TenantId] = Some(TenantId("SomeTenant"))
  ): MemberInfo = {
    MemberInfo(
      handle = handle,
      avatarUrl = avatarUrl,
      firstName = firstName,
      lastName = lastName,
      notificationPreference = notificationPreference,
      notificationOptIn = optIn,
      contact = Some(
        Contact(
          firstName = firstName,
          lastName = lastName,
          emailAddress = email,
          phone = phNo,
          userName = userName,
        )
      ),
      organizations = organizations,
      tenant = tentant
    )
  }
}
