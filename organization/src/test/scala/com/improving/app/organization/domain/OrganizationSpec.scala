package com.improving.app.organization.domain

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.typed.{Cluster, Join}
import akka.pattern.StatusReply
import com.improving.app.common.domain._
import com.improving.app.organization.{OrganizationResponse, _}
import com.improving.app.organization.domain.Organization.{OrganizationCommand, OrganizationEntityKey}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory
import TestData._
import cats.data.Validated
import com.improving.app.organization.domain.OrganizationValidation.StringIsEmptyError

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class OrganizationSpec extends ScalaTestWithActorTestKit(OrganizationSpec.config) with AnyWordSpecLike with Matchers {

  private val log = LoggerFactory.getLogger(getClass)

  val organizationId: String = "test-organization-id"

  val organizationId2: String = "test-organization-id2"

  val waitDuration: FiniteDuration = 5.seconds

  val sharding: ClusterSharding = ClusterSharding(system)

  "Organization Service" should {

    ClusterSharding(system).init(
      Entity(OrganizationEntityKey)(entityContext =>
        domain.Organization(
          entityContext.entityId
        )
      )
    )

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)

    "allow Organization to be established" in {

      val establishOrganizationRequest = TestData.establishOrganizationRequest

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(establishOrganizationRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(
                OrganizationEventResponse(
                  organizationEstablished,
                  _
                )
              ) =>
            log.info(
              s"StatusReply.Success establishedOrganization ${organizationEstablished}"
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "not allow Organization to be re-established" in {

      val establishOrganizationRequest = TestData.establishOrganizationRequest

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(establishOrganizationRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Error(error) =>
            log.error(
              s"Organization establish failed with error ${error.getMessage}",
              error
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "return correct Organization from getOrganzation request" in {

      val getOrganizationByIdRequest =
        GetOrganizationByIdRequest(Some(OrganizationId(organizationId)))

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(getOrganizationByIdRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(organization) =>
            log.info(
              s"Organization returned $organization",
              organization
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "return correct OrganizationInfo from getOrganzationInfo request" in {

      val getOrganizationInfoRequest =
        GetOrganizationInfoRequest(Some(OrganizationId(organizationId)))

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(getOrganizationInfoRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(organizationInfo) =>
            log.info(
              s"OrganizationInfo returned $organizationInfo",
              organizationInfo
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "add new members correctly to Organization" in {

      val addMembersToOrganizationRequest =
        AddMembersToOrganizationRequest(
          Some(OrganizationId(organizationId)),
          newMembers
        )

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(addMembersToOrganizationRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(
                OrganizationEventResponse(
                  membersAddedToOrganization: MembersAddedToOrganization,
                  _
                )
              ) =>
            log.info(
              s"membersAddedToOrganization returned $membersAddedToOrganization",
              membersAddedToOrganization
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }
    "remove members correctly from Organization" in {

      val removeMembersFromOrganizationRequest =
        RemoveMembersFromOrganizationRequest(
          Some(OrganizationId(organizationId)),
          newMembers
        )

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(removeMembersFromOrganizationRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(
                OrganizationEventResponse(
                  membersRemovedFromOrganization: MembersRemovedFromOrganization,
                  _
                )
              ) =>
            log.info(
              s"membersRemovedFromOrganization returned $membersRemovedFromOrganization",
              membersRemovedFromOrganization
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "add new owners correctly to Organization" in {

      val addOwnersToOrganizationRequest =
        AddOwnersToOrganizationRequest(
          Some(OrganizationId(organizationId)),
          newMembers
        )

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(addOwnersToOrganizationRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(
                OrganizationEventResponse(
                  ownersAddedToOrganization: OwnersAddedToOrganization,
                  _
                )
              ) =>
            log.info(
              s"ownersAddedToOrganization returned $ownersAddedToOrganization",
              ownersAddedToOrganization
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "remove owners correctly from Organization" in {

      val removeOwnersFromOrganizationRequest =
        RemoveOwnersFromOrganizationRequest(
          Some(OrganizationId(organizationId)),
          newMembers
        )

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(removeOwnersFromOrganizationRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(
                OrganizationEventResponse(
                  ownersRemovedFromOrganization: OwnersRemovedFromOrganization,
                  _
                )
              ) =>
            log.info(
              s"ownersRemovedFromOrganization returned $ownersRemovedFromOrganization",
              ownersRemovedFromOrganization
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }

    "update info correctly in Organization" in {

      val editOrganizationInfoRequest =
        EditOrganizationInfoRequest(
          Some(OrganizationId(organizationId)),
          Some(testInfo),
          Some(testActingMember4)
        )

      val probe = TestProbe[StatusReply[OrganizationResponse]]()
      val ref: EntityRef[OrganizationCommand] =
        sharding.entityRefFor(OrganizationEntityKey, organizationId)

      ref.ask[OrganizationResponse](_ => OrganizationCommand(editOrganizationInfoRequest, probe.ref))

      val result: Seq[StatusReply[OrganizationResponse]] =
        probe.fishForMessagePF(waitDuration) {
          case StatusReply.Success(
                OrganizationEventResponse(
                  organizationInfoUpdated: OrganizationInfoUpdated,
                  _
                )
              ) =>
            log.info(
              s"organizationInfoUpdated returned $organizationInfoUpdated",
              organizationInfoUpdated
            )
            FishingOutcome.Complete
          case other =>
            log.info(s"Skipping $other")
            FishingOutcome.ContinueAndIgnore
        }
      result.length shouldBe 1
    }
  }

  "update parent correctly in Organization" in {

    val updateParentRequest =
      UpdateParentRequest(
        Some(OrganizationId(organizationId)),
        Some(testNewParent),
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(updateParentRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                parentUpdated: ParentUpdated,
                _
              )
            ) =>
          log.info(
            s"parentUpdated returned $parentUpdated",
            parentUpdated
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "activate Organization correctly" in {

    val activateOrganizationRequest =
      ActivateOrganizationRequest(
        Some(OrganizationId(organizationId)),
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(activateOrganizationRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationActivated: OrganizationActivated,
                _
              )
            ) =>
          log.info(
            s"organizationActivated returned $organizationActivated",
            organizationActivated
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "suspend Organization correctly" in {

    val suspendOrganizationRequest =
      SuspendOrganizationRequest(
        Some(OrganizationId(organizationId)),
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(suspendOrganizationRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationSuspended: OrganizationSuspended,
                _
              )
            ) =>
          log.info(
            s"organizationSuspended returned $organizationSuspended",
            organizationSuspended
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "terminate Organization correctly" in {

    val terminateOrganizationRequest =
      TerminateOrganizationRequest(
        Some(OrganizationId(organizationId)),
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(terminateOrganizationRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationTerminated: OrganizationTerminated,
                _
              )
            ) =>
          log.info(
            s"organizationTerminated returned $organizationTerminated",
            organizationTerminated
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "release Organization correctly" in {

    val releaseOrganizationRequest =
      ReleaseOrganizationRequest(
        Some(OrganizationId(organizationId)),
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(releaseOrganizationRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationReleased: OrganizationReleased,
                _
              )
            ) =>
          log.info(
            s"organizationReleased returned $organizationReleased",
            organizationReleased
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "update Organization status correctly" in {

    val establishOrganizationRequest = TestData.establishOrganizationRequest

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId2)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(establishOrganizationRequest, probe.ref))

    val updateOrganizationStatusRequest =
      UpdateOrganizationStatusRequest(
        Some(OrganizationId(organizationId2)),
        OrganizationStatus.ORGANIZATION_STATUS_ACTIVE,
        Some(testActingMember3)
      )

    ref.ask[OrganizationResponse](_ => OrganizationCommand(updateOrganizationStatusRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationStatusUpdated: OrganizationStatusUpdated,
                _
              )
            ) =>
          log.info(
            s"organizationStatusUpdated returned $organizationStatusUpdated",
            organizationStatusUpdated
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "update Organization contact correctly" in {

    val updateOrganizationContactsRequest =
      UpdateOrganizationContactsRequest(
        Some(OrganizationId(organizationId2)),
        testNewContacts,
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId2)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(updateOrganizationContactsRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationContactsUpdated: OrganizationContactsUpdated,
                _
              )
            ) =>
          log.info(
            s"organizationContactsUpdated returned $organizationContactsUpdated",
            organizationContactsUpdated
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "update Organization account correctly" in {

    val updateOrganizationAccountsRequest =
      UpdateOrganizationAccountsRequest(
        Some(OrganizationId(organizationId2)),
        Some(testNewTestInfo),
        Some(testActingMember3)
      )

    val probe = TestProbe[StatusReply[OrganizationResponse]]()
    val ref: EntityRef[OrganizationCommand] =
      sharding.entityRefFor(OrganizationEntityKey, organizationId2)

    ref.ask[OrganizationResponse](_ => OrganizationCommand(updateOrganizationAccountsRequest, probe.ref))

    val result: Seq[StatusReply[OrganizationResponse]] =
      probe.fishForMessagePF(waitDuration) {
        case StatusReply.Success(
              OrganizationEventResponse(
                organizationAccountsUpdated: OrganizationAccountsUpdated,
                _
              )
            ) =>
          log.info(
            s"organizationAccountsUpdated returned $organizationAccountsUpdated",
            organizationAccountsUpdated
          )
          FishingOutcome.Complete
        case other =>
          log.info(s"Skipping $other")
          FishingOutcome.ContinueAndIgnore
      }
    result.length shouldBe 1
  }

  "fail validation for organization" in {
    OrganizationValidation.validateInfo(Info()) match {
      case Validated.Valid(a)   => fail("Validation should fail with info with empty name")
      case Validated.Invalid(e) => e.leftSideValue.toNonEmptyList.head shouldBe StringIsEmptyError("name")
    }
  }
}

object OrganizationSpec {
  val config: Config = ConfigFactory.parseString("""
        akka.loglevel = INFO
        #akka.persistence.typed.log-stashing = on
        akka.actor.provider = cluster
        akka.remote.artery.canonical.port = 0
        akka.remote.artery.canonical.hostname = 127.0.0.1

        akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
        akka.persistence.journal.inmem.test-serialization = on

        akka.actor.serialization-bindings{
          "com.improving.app.common.serialize.PBMsgSerializable" = proto
          "com.improving.app.common.serialize.PBMsgOneOfSerializable" = proto
          "com.improving.app.common.serialize.PBEnumSerializable" = proto
        }
      """)
}
