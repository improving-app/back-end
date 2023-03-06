package com.improving.app.member.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.time.Instant

object Member {

  /**
   * State Diagram
   *
   * Initial -> Active -> Inactive -> Suspended -> Terminated
   */

  val MemberEntityKey: EntityTypeKey[MemberCommand] = EntityTypeKey[MemberCommand]("Member")

  //register entity
  def initSharding(sharding: ClusterSharding): Unit =
    sharding.init(Entity(MemberEntityKey) { entityContext =>
      Member(entityContext.entityTypeKey.name, entityContext.entityId)
    })

  //Command wraps the type
  case class MemberCommand(request: MemberRequest, replyTo: ActorRef[StatusReply[MemberResponse]])

  private def emptyState(entityId: String): MemberState =
    MemberState(Some(MemberId(entityId)), None, None)

  def apply(entityTypeHint: String, memberId: String): Behavior[MemberCommand] =
    Behaviors.setup { context =>
      context.log.info("Starting Member {}", memberId)
      EventSourcedBehavior[MemberCommand, MemberEvent, MemberState](
        persistenceId = PersistenceId(entityTypeHint, memberId),
        emptyState = emptyState(memberId),
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    }

  //Check if the command is valid for the current state
  def isCommandValidForState(state: MemberState, command: MemberRequest): Boolean = {
    command match {
      case RegisterMember(_, _)   => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_INITIAL)
      case ActivateMember(_, _)   => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_INITIAL)
      case InactivateMember(_, _) => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_ACTIVE)
      case SuspendMember(_, _)    => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_INACTIVE)
      case TerminateMember(_, _)  => state.memberMetaInfo.exists(_.memberState == MemberStatus.MEMBER_STATUS_SUSPENDED)
      case UpdateMemberInfo(_, _) => state.memberMetaInfo.exists(_.memberState != MemberStatus.MEMBER_STATUS_TERMINATED)
      case GetMemberInfo(_)       => true
    }
  }

  //CommandHandler
  private val commandHandler: (MemberState, MemberCommand) => Effect[MemberEvent, MemberState] = {
    (state, command: MemberCommand) =>
      command.request match {
        case cmd if !isCommandValidForState(state, cmd) =>
          Effect.reply(command.replyTo) { StatusReply.Error(s"Invalid Command ${command.request} for State $state") }
        case RegisterMember(Some(memberInfo: MemberInfo), Some(registeringMember)) =>
          registerMember(memberInfo, registeringMember, state, command.replyTo)
        case ActivateMember(Some(memberId: MemberId), Some(activatingMemberId)) =>
          activateMember(memberId, activatingMemberId, state, command.replyTo)
        case InactivateMember(Some(memberId: MemberId), Some(inactivatingMemberId)) =>
          inactivateMember(memberId, inactivatingMemberId, state, command.replyTo)
        case SuspendMember(Some(memberId: MemberId), Some(suspendingMemberId)) =>
          suspendMember(memberId, suspendingMemberId, state, command.replyTo)
        case TerminateMember(Some(memberId: MemberId), Some(terminatingMemberId)) =>
          terminateMember(memberId, terminatingMemberId, state, command.replyTo)
        case UpdateMemberInfo(
              Some(MemberMap(Some(memberId: MemberId), Some(memberInfo: MemberInfo))),
              Some(updatingMember)
            ) =>
          updateMemberInfo(memberId, memberInfo, updatingMember, state, command.replyTo)
        case GetMemberInfo(Some(memberId)) =>
          getMemberInfo(memberId, state, command.replyTo)
        case _ =>
          Effect.reply(command.replyTo) { StatusReply.Error(s"Invalid Member Command ${command.request}") }
      }
  }

  private def createMemberMetaInfo(createdBy: MemberId): MemberMetaInfo = {
    val currentTime = Instant.now().toEpochMilli
    MemberMetaInfo(
      currentTime,
      Some(createdBy),
      currentTime,
      Some(createdBy),
      null
    ).withMemberState(MemberStatus.MEMBER_STATUS_INITIAL) //FIXME
  }

  private def updateMemberMetaInfo(
      currentMetaInfo: MemberMetaInfo,
      updatedBy: MemberId,
      updatedStatus: MemberStatus
  ): MemberMetaInfo = {
    val currentTime = Instant.now().toEpochMilli
    currentMetaInfo.copy(
      lastModifiedBy = Some(updatedBy),
      lastModifiedOn = currentTime,
      memberState = updatedStatus
    )
  }

  def registerMember(
      memberInfo: MemberInfo,
      actingMember: MemberId,
      state: MemberState,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {
    val event = MemberRegistered(state.memberId, Some(memberInfo), Some(createMemberMetaInfo(actingMember)))
    Effect.persist(event).thenRun { _ => replyTo ! StatusReply.Success(MemberEventResponse(event)) }
  }

  def activateMember(
      memberId: MemberId,
      activatingMember: MemberId,
      state: MemberState,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {
    val event = MemberActivated(
      Some(memberId),
      Some(updateMemberMetaInfo(state.memberMetaInfo.get, activatingMember, MemberStatus.MEMBER_STATUS_ACTIVE))
    )
    Effect
      .persist(event)
      .thenRun { _ => replyTo ! StatusReply.Success(MemberEventResponse(event)) }
  }

  def inactivateMember(
      memberId: MemberId,
      actingMember: MemberId,
      state: MemberState,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {

    val event = MemberInactivated(
      Some(memberId),
      Some(
        updateMemberMetaInfo(
          state.memberMetaInfo.get,
          actingMember,
          MemberStatus.MEMBER_STATUS_INACTIVE
        )
      )
    )
    Effect
      .persist(event)
      .thenRun { _ => replyTo ! StatusReply.Success(MemberEventResponse(event)) }

  }

  def suspendMember(
      memberId: MemberId,
      suspendingMemberId: MemberId,
      state: MemberState,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {
    val event = MemberSuspended(
      Some(memberId),
      Some(updateMemberMetaInfo(state.memberMetaInfo.get, suspendingMemberId, MemberStatus.MEMBER_STATUS_SUSPENDED))
    )
    Effect
      .persist(event)
      .thenRun { _ => replyTo ! StatusReply.Success(MemberEventResponse(event)) }

  }
  def terminateMember(
      memberId: MemberId,
      terminatingMember: MemberId,
      state: MemberState,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {
    val event = MemberTerminated(
      Some(memberId),
      Some(updateMemberMetaInfo(state.memberMetaInfo.get, terminatingMember, MemberStatus.MEMBER_STATUS_TERMINATED))
    )
    Effect.persist(event).thenRun { _ => replyTo ! StatusReply.Success(MemberEventResponse(event)) }

  }

  def updateMemberInfo(
      memberId: MemberId,
      memberInfo: MemberInfo,
      actingMember: MemberId,
      state: MemberState,
      replyTo: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {
    val event = MemberInfoUpdated(
      Some(memberId),
      Some(memberInfo),
      Some(
        updateMemberMetaInfo(
          state.memberMetaInfo.get,
          actingMember,
          state.memberMetaInfo.get.memberState
        )
      )
    )
    Effect.persist(event).thenRun { _ => replyTo ! StatusReply.Success(MemberEventResponse(event)) }

  }

  def getMemberInfo(
      memberId: MemberId,
      state: MemberState,
      value: ActorRef[StatusReply[MemberResponse]]
  ): Effect[MemberEvent, MemberState] = {
    Effect.reply(value) {
      StatusReply.Success(
        MemberData(Some(memberId), state.memberInfo, state.memberMetaInfo)
      )
    }
  }

  //EventHandler
  private val eventHandler: (MemberState, MemberEvent) => MemberState = { (state, event) =>
    event match {

      case MemberRegistered(_, Some(memberInfo), Some(memberMetaInfo)) =>
        state.withMemberInfo(memberInfo).withMemberMetaInfo(memberMetaInfo)

      case MemberActivated(_, Some(memberMetaInfo)) =>
        state.withMemberMetaInfo(memberMetaInfo)

      case MemberInactivated(_, Some(memberMetaInfo)) =>
        state.withMemberMetaInfo(memberMetaInfo)

      case MemberSuspended(_, Some(memberMetaInfo)) =>
        state.withMemberMetaInfo(memberMetaInfo)

      case MemberTerminated(_, Some(memberMetaInfo)) =>
        state.withMemberMetaInfo(memberMetaInfo)

      case MemberInfoUpdated(_, Some(memberInfo), Some(memberMetaInfo)) =>
        state.withMemberInfo(memberInfo).withMemberMetaInfo(memberMetaInfo)
    }
  }
}
