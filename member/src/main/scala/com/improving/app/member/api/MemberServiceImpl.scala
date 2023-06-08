package com.improving.app.member.api
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.pattern.StatusReply
import akka.util.Timeout
import com.improving.app.member.domain.Member.{HasMemberId, MemberCommand, MemberEntityKey}
import com.improving.app.member.domain._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MemberServiceImpl(implicit val system: ActorSystem[_]) extends MemberService {

  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  //Create a new member
  val sharding: ClusterSharding = ClusterSharding(system)
  ClusterSharding(system).init(
    Entity(MemberEntityKey)(entityContext => Member(entityContext.entityTypeKey.name, entityContext.entityId))
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  //Do not use for RegisterMember

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
      eventHandler: PartialFunction[StatusReply[MemberResponse], T],
      extractMemberId: MemberRequest => String = {
        case req: HasMemberId =>
          req.memberId match {
            case Some(id) => id.id
            case None     => throw new RuntimeException(s"Member request does include a memberId (it is None)")
          }
        case other => throw new RuntimeException(s"Member request does not implement HasMemberId $other")
      }
  ): Future[T] = {
    val memberEntity = sharding.entityRefFor(MemberEntityKey, extractMemberId(in))

    //Register the member
    memberEntity
      .ask[StatusReply[MemberResponse]](replyTo => MemberCommand(in, replyTo))
      .map { handleResponse(eventHandler) }
  }

  override def registerMember(in: RegisterMember): Future[MemberRegistered] = {
    handleRequest(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberRegistered(_, _, _, _), _)) =>
        response
      }
    )
  }

  override def activateMember(in: ActivateMember): Future[MemberActivated] =
    handleRequest(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberActivated(_, _, _), _)) => response }
    )

  override def suspendMember(in: SuspendMember): Future[MemberSuspended] =
    handleRequest(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberSuspended(_, _, _), _)) => response }
    )

  override def terminateMember(in: TerminateMember): Future[MemberTerminated] =
    handleRequest(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberTerminated(_, _, _), _)) => response }
    )

  override def editMemberInfo(
      in: EditMemberInfo
  ): Future[MemberInfoEdited] =
    handleRequest(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberInfoEdited(_, _, _, _), _)) => response }
    )

  override def getMemberInfo(in: GetMemberInfo): Future[MemberData] =
    handleRequest(
      in,
      { case StatusReply.Success(response @ MemberData(_, _, _, _)) => response }
    )
}
