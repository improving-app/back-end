package com.improving.app.member.api
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.util.Timeout
import com.google.rpc.Code
import com.improving.app.member.domain.Member.{MemberEntityKey, MemberEnvelope}
import com.improving.app.member.domain._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps

class MemberServiceImpl(implicit val system: ActorSystem[_]) extends MemberService {

  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)
  implicit val executor: ExecutionContextExecutor = system.executionContext

  // Create a new member
  val sharding: ClusterSharding = ClusterSharding(system)
  ClusterSharding(system).init(
    Entity(MemberEntityKey)(entityContext => Member(entityContext.entityTypeKey.name, entityContext.entityId))
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  // Do not use for RegisterMember

  private def handleResponse[T](
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): PartialFunction[StatusReply[MemberResponse], T] = {
    eventHandler.orElse {
      case StatusReply.Success(response) => throw new RuntimeException(s"Unexpected response $response")
      case StatusReply.Error(ex)         => throw ex
    }
  }

  private def handleCommand[T](
      in: MemberRequestPB with MemberCommand,
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): Future[T] = in.memberId
    .map { id =>
      val memberEntity = sharding.entityRefFor(MemberEntityKey, id.id)

      memberEntity
        .ask[StatusReply[MemberResponse]](replyTo => MemberEnvelope(in, replyTo))
        .map { handleResponse(eventHandler) }
    }
    .getOrElse(
      Future.failed(
        GrpcServiceException.create(
          Code.INVALID_ARGUMENT,
          "An entity Id was not provided",
          java.util.List.of(in.asMessage)
        )
      )
    )

  private def handleQuery[T](
      in: MemberRequestPB with MemberQuery,
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): Future[T] = in.memberId
    .map { id =>
      val memberEntity = sharding.entityRefFor(MemberEntityKey, id.id)

      memberEntity
        .ask[StatusReply[MemberResponse]](replyTo => MemberEnvelope(in, replyTo))
        .map { handleResponse(eventHandler) }
    }
    .getOrElse(
      Future.failed(
        GrpcServiceException.create(
          Code.INVALID_ARGUMENT,
          "An entity Id was not provided",
          java.util.List.of(in.asMessage)
        )
      )
    )

  override def registerMember(in: RegisterMember): Future[MemberRegistered] = {
    handleCommand(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberRegistered(_, _, _, _), _)) =>
        response
      }
    )
  }

  override def activateMember(in: ActivateMember): Future[MemberActivated] =
    handleCommand(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberActivated(_, _, _), _)) => response }
    )

  override def suspendMember(in: SuspendMember): Future[MemberSuspended] =
    handleCommand(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberSuspended(_, _, _, _), _)) => response }
    )

  override def terminateMember(in: TerminateMember): Future[MemberTerminated] =
    handleCommand(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberTerminated(_, _, _), _)) => response }
    )

  override def editMemberInfo(
      in: EditMemberInfo
  ): Future[MemberInfoEdited] =
    handleCommand(
      in,
      { case StatusReply.Success(MemberEventResponse(response @ MemberInfoEdited(_, _, _, _), _)) => response }
    )

  override def getMemberInfo(in: GetMemberInfo): Future[MemberData] =
    handleQuery(
      in,
      { case StatusReply.Success(response @ MemberData(_, _, _, _)) => response }
    )
}
