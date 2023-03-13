package com.improving.app.member.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import cats.data.NonEmptyChain
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toFoldableOps
import com.google.protobuf.timestamp.Timestamp
import com.improving.app.member.domain.MemberStatus._
import com.typesafe.scalalogging.StrictLogging

import java.time.{Clock, Instant}

object Member extends StrictLogging {

  private val clock: Clock = Clock.systemDefaultZone()

  val MemberEntityKey: EntityTypeKey[MemberCommand] = EntityTypeKey[MemberCommand]("Member")

  //Command wraps the request type
  final case class MemberCommand(request: MemberRequest, replyTo: ActorRef[StatusReply[MemberResponse]])

  private def emptyState(entityId: String): MemberState =
    MemberState(Some(MemberId(entityId)), None, None)

  /* def validateMemberInfo(memberInfo: MemberInfo): Validated[] = {

    memberInfo.firstName.nonEmpty && memberInfo.lastName.nonEmpty && memberInfo.email.nonEmpty
  }*/

  def apply(entityTypeHint: String, memberId: String): Behavior[MemberCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting Member {}", memberId)
      EventSourcedBehavior
        .withEnforcedReplies[MemberCommand, MemberEvent, MemberState](
          persistenceId = PersistenceId(entityTypeHint, memberId),
          emptyState = emptyState(memberId),
          commandHandler = commandHandler,
          eventHandler = eventHandler
        )
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            context.log.debug("onRecoveryCompleted: [{}]", state)
          case (_, PostStop) =>
            context.log.info("Member {} stopped", memberId)
        }
    }

  //Check if the command is valid for the current state
  //TODO validate state transitions
  /**
   * State Diagram
   *
   * Initial -> Active <-> Inactive <-> Suspended -> Terminated
   */
  private def isCommandValidForState(state: MemberState, command: MemberRequest): Boolean = {
    command match {
      case RegisterMember(_, _) => state.memberMetaInfo.isEmpty
      case ActivateMember(_, _) =>
        state.memberMetaInfo.exists(metaInf =>
          metaInf.memberState == MemberStatus.MEMBER_STATUS_INITIAL || metaInf.memberState == MemberStatus.MEMBER_STATUS_INACTIVE
        )
      case InactivateMember(_, _) =>
        state.memberMetaInfo.exists(metaInf =>
          metaInf.memberState == MemberStatus.MEMBER_STATUS_ACTIVE || metaInf.memberState == MemberStatus.MEMBER_STATUS_SUSPENDED
        )
      case SuspendMember(_, _)   => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_INACTIVE)
      case TerminateMember(_, _) => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_SUSPENDED)
      case UpdateMemberInfo(_, _, _) =>
        state.memberMetaInfo.exists(_.memberState != MemberStatus.MEMBER_STATUS_TERMINATED)
      case GetMemberInfo(_) => true
      case other =>
        logger.error(s"Invalid Member Command $other")
        false
    }
  }

  //CommandHandler
  private val commandHandler: (MemberState, MemberCommand) => ReplyEffect[MemberEvent, MemberState] = {
    (state, command: MemberCommand) =>
      command.request match {
        case cmd if !isCommandValidForState(state, cmd) =>
          Effect.reply(command.replyTo) { StatusReply.Error(s"Invalid Command ${command.request} for State $state") }
        case RegisterMember(Some(memberInfo: MemberInfo), Some(registeringMember)) =>
          registerMember(state.memberId.get, memberInfo, registeringMember, command.replyTo)
        case ActivateMember(Some(memberId: MemberId), Some(activatingMemberId)) if state.memberId.contains(memberId) =>
          activateMember(memberId, activatingMemberId, command.replyTo)
        case InactivateMember(Some(memberId: MemberId), Some(inactivatingMemberId))
            if state.memberId.contains(memberId) =>
          inactivateMember(memberId, inactivatingMemberId, command.replyTo)
        case SuspendMember(Some(memberId: MemberId), Some(suspendingMemberId)) if state.memberId.contains(memberId) =>
          suspendMember(memberId, suspendingMemberId, command.replyTo)
        case TerminateMember(Some(memberId: MemberId), Some(terminatingMemberId))
            if state.memberId.contains(memberId) =>
          terminateMember(memberId, terminatingMemberId, command.replyTo)
        case UpdateMemberInfo(Some(memberId: MemberId), Some(memberInfo: MemberInfo), Some(updatingMember))
            if state.memberId.contains(memberId) =>
          updateMemberInfo(memberId, memberInfo, updatingMember, command.replyTo)
        case GetMemberInfo(Some(memberId)) if state.memberId.contains(memberId) =>
          getMemberInfo(memberId, state, command.replyTo)
        case _ =>
          Effect.reply(command.replyTo) {
            StatusReply.Error(s"Invalid Command ${command.request} for State: $state")
          }
      }
  }

  def registerMember(
      memberId: MemberId,
      memberInfo: MemberInfo,
      actingMember: MemberId,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] =
    MemberValidation.validateMemberInfo(memberInfo) match {
      case Valid(memberInfo) =>
        val event =
          MemberRegistered(Some(memberId), Some(memberInfo), Some(actingMember), Some(Timestamp(Instant.now(clock))))
        Effect.persist(event).thenReply(replyTo) { _ =>
          val res = StatusReply.Success(MemberEventResponse(event))
          logger.info(s"registerMember: $res")
          res
        }
      case Invalid(errors: NonEmptyChain[MemberValidation.MemberValidationError]) =>
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"Invalid Member Info: $memberInfo with errors: ${errors.map { _.errorMessage }.toList.mkString(",")}"
          )
        }
    }

  def activateMember(
      memberId: MemberId,
      activatingMember: MemberId,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    val event = MemberActivated(Some(memberId), Some(activatingMember), Some(Timestamp(Instant.now(clock))))

    Effect
      .persist(event)
      .thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }
  }

  def inactivateMember(
      memberId: MemberId,
      actingMember: MemberId,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {

    val event = MemberInactivated(Some(memberId), Some(actingMember), Some(Timestamp(Instant.now(clock))))
    Effect
      .persist(event)
      .thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }

  }

  def suspendMember(
      memberId: MemberId,
      suspendingMemberId: MemberId,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    val event = MemberSuspended(Some(memberId), Some(suspendingMemberId), Some(Timestamp(Instant.now(clock))))
    Effect
      .persist(event)
      .thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }

  }
  def terminateMember(
      memberId: MemberId,
      terminatingMember: MemberId,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    val event = MemberTerminated(Some(memberId), Some(terminatingMember), Some(Timestamp(Instant.now(clock))))
    Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }

  }

  def updateMemberInfo(
      memberId: MemberId,
      memberInfo: MemberInfo,
      actingMember: MemberId,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] =
    MemberValidation.validateMemberInfo(memberInfo) match {
      case Valid(memberInfo) =>
        val event =
          MemberInfoUpdated(Some(memberId), Some(memberInfo), Some(actingMember), Some(Timestamp(Instant.now(clock))))
        Effect.persist(event).thenReply(replyTo) { _ => StatusReply.Success(MemberEventResponse(event)) }
      case Invalid(errors: NonEmptyChain[MemberValidation.MemberValidationError]) =>
        Effect.reply(replyTo) {
          StatusReply.Error(
            s"Invalid Member Info: $memberInfo with errors: ${errors
              .map {
                _.errorMessage
              }
              .toList
              .mkString(",")}"
          )
        }
    }

  def getMemberInfo(
      memberId: MemberId,
      state: MemberState,
      value: ActorRef[StatusReply[MemberResponse]]
  ): ReplyEffect[MemberEvent, MemberState] = {
    Effect.reply(value) {
      StatusReply.Success(
        MemberData(Some(memberId), state.memberInfo, state.memberMetaInfo)
      )
    }
  }

  private def createMemberMetaInfo(createdBy: MemberId, createdOn: Timestamp): MemberMetaInfo =
    MemberMetaInfo(
      Some(createdOn),
      Some(createdBy),
      Some(createdOn),
      Some(createdBy),
      MemberStatus.MEMBER_STATUS_INITIAL
    )

  //Will fail if invalid state
  private def updateMemberMetaInfo(
      state: MemberState,
      updatedBy: MemberId,
      updatedOn: Timestamp,
      updatedStatus: MemberStatus
  ): MemberState =
    state.withMemberMetaInfo(
      state.memberMetaInfo.get
        .withLastModifiedBy(updatedBy)
        .withLastModifiedOn(updatedOn)
        .withMemberState(updatedStatus)
    )

  //EventHandler
  private val eventHandler: (MemberState, MemberEvent) => MemberState = { (state, event) =>
    event match {

      case MemberRegistered(_, Some(memberInfo), Some(actingMember), Some(createdOn)) =>
        state.withMemberInfo(memberInfo).withMemberMetaInfo(createMemberMetaInfo(actingMember, createdOn))

      case MemberActivated(_, Some(actingMember), Some(updatedOn)) =>
        updateMemberMetaInfo(state, actingMember, updatedOn, MEMBER_STATUS_ACTIVE)

      case MemberInactivated(_, Some(actingMember), Some(updatedOn)) =>
        updateMemberMetaInfo(state, actingMember, updatedOn, MEMBER_STATUS_INACTIVE)

      case MemberSuspended(_, Some(actingMember), Some(updatedOn)) =>
        updateMemberMetaInfo(state, actingMember, updatedOn, MEMBER_STATUS_SUSPENDED)

      case MemberTerminated(_, Some(actingMember), Some(updatedOn)) =>
        updateMemberMetaInfo(state, actingMember, updatedOn, MEMBER_STATUS_TERMINATED)

      case MemberInfoUpdated(_, Some(memberInfo), Some(actingMember), Some(updatedOn)) =>
        updateMemberMetaInfo(
          state.withMemberInfo(memberInfo),
          actingMember,
          updatedOn,
          state.memberMetaInfo.get.memberState
        )

      case other =>
        throw new RuntimeException(s"Invalid/Unhandled event $other")
    }
  }
}
