package com.improving.app.member.api
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.util.Timeout
import com.google.protobuf.empty.Empty
import com.google.rpc.Code
import com.improving.app.common.domain.MemberId
import com.improving.app.member.domain.Member.{MemberEntityKey, MemberEnvelope}
import com.improving.app.member.domain._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps

class MemberServiceImpl(sys: ActorSystem[_]) extends MemberService {
  implicit private val system: ActorSystem[_] = sys
  implicit val ec: ExecutionContext = sys.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)
  implicit val executor: ExecutionContextExecutor = sys.executionContext

  // Create a new member
  val sharding: ClusterSharding = ClusterSharding(sys)
  ClusterSharding(sys).init(
    Entity(MemberEntityKey)(entityContext => Member(entityContext.entityTypeKey.name, entityContext.entityId))
  )

  Cluster(sys).manager ! Join(Cluster(sys).selfMember.address)

  private def handleResponse[T](
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): PartialFunction[StatusReply[MemberResponse], T] = {
    eventHandler.orElse {
      case StatusReply.Success(response) => throw new RuntimeException(s"Unexpected response $response")
      case StatusReply.Error(ex)         => throw ex
    }
  }

  private def handleCommand[T](
      in: MemberCommand with MemberRequestPB,
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): Future[T] = in.memberId
    .map { id =>
      val memberEntity = sharding.entityRefFor(MemberEntityKey, id.id)

      memberEntity
        .ask[StatusReply[MemberResponse]](replyTo => MemberEnvelope(in.asInstanceOf[MemberRequestPB], replyTo))
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
      in: MemberQuery with MemberRequestPB,
      eventHandler: PartialFunction[StatusReply[MemberResponse], T]
  ): Future[T] = in.memberId
    .map { id =>
      val memberEntity = sharding.entityRefFor(MemberEntityKey, id.id)

      memberEntity
        .ask(replyTo => MemberEnvelope(in.asInstanceOf[MemberRequestPB], replyTo))
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

  override def getAllIds(in: Empty): Future[AllMemberIds] = {
    val readJournal =
      PersistenceQuery(sys).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    readJournal.currentPersistenceIds().runFold(Seq[MemberId]())(_ :+ MemberId(_)).map { seq =>
      AllMemberIds(seq)
    }
  }
}
