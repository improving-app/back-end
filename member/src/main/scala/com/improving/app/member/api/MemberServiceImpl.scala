package com.improving.app.member.api
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.pattern.StatusReply
import akka.util.Timeout
import com.improving.app.member.domain.Member.{MemberCommand, MemberEntityKey}
import com.improving.app.member.domain._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MemberServiceImpl(implicit val system: ActorSystem[_]) extends MemberService {

  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 seconds)

  //Create a new member
  val sharding: ClusterSharding = ClusterSharding(system)
  Member.initSharding(sharding)

  //Do not use for RegisterMember
  private def extractEntityId(request: MemberRequest): String = {
    request match {
      case RegisterMember(_, _)          => None
      case ActivateMember(memberId, _)   => memberId.map(_.id)
      case InactivateMember(memberId, _) => memberId.map(_.id)
      case SuspendMember(memberId, _)    => memberId.map(_.id)
      case TerminateMember(memberId, _)  => memberId.map(_.id)
      case GetMemberInfo(memberId)       => memberId.map(_.id)
    }
  }.getOrElse(throw new RuntimeException(s"Missing member id in request $request"))

  private def handleResponse[T](
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): PartialFunction[StatusReply[MemberResponse], T] = {
    eventHandler.orElse({
      case StatusReply.Success(response) => throw new RuntimeException(s"Unexpected response $response")
      case StatusReply.Error(ex)         => throw ex
    })
  }

  private def handleRequest[T](
      in: MemberRequest,
      memberId: String,
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ) = {
    val memberEntity = sharding.entityRefFor(MemberEntityKey, memberId)

    //Register the member
    memberEntity
      .ask[StatusReply[MemberResponse]](replyTo => MemberCommand(in, replyTo))
      .map { handleResponse(eventHandler) }
  }

  override def registerMember(in: RegisterMember): Future[MemberRegistered] = {
    //create a member with a generated Id
    //TODO check collision - for now assumed to be unique
    val memberId = java.util.UUID.randomUUID.toString

    handleRequest(
      in,
      memberId,
      { case StatusReply.Success(MemberEventResponse(response @ MemberRegistered(_, _, _))) =>
        response
      }
    )
  }

  override def activateMember(in: ActivateMember): Future[MemberActivated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberActivated(_, _))) => response }
    )

  override def inactivateMember(
      in: InactivateMember
  ): Future[MemberInactivated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberInactivated(_, _))) => response }
    )

  override def suspendMember(in: SuspendMember): Future[MemberSuspended] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberSuspended(_, _))) => response }
    )

  override def terminateMember(in: TerminateMember): Future[MemberTerminated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberTerminated(_, _))) => response }
    )

  override def updateMemberInfo(
      in: UpdateMemberInfo
  ): Future[MemberInfoUpdated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberInfoUpdated(_, _, _))) => response }
    )
  override def getMemberInfo(in: GetMemberInfo): Future[MemberData] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(response @ MemberData(_, _, _)) => response }
    )
}
