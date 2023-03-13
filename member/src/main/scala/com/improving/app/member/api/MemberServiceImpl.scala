package com.improving.app.member.api
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.pattern.StatusReply
import akka.serialization.jackson.JacksonObjectMapperProvider
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.improving.app.member.domain.Member.{MemberCommand, MemberEntityKey}
import com.improving.app.member.domain._
import com.improving.app.member.utils.serialize.AvroJacksonObjectMapperFactory.applyObjectMapperMixins
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MemberServiceImpl(implicit val system: ActorSystem[_]) extends MemberService {

  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 seconds)

  //Create a new member
  val sharding: ClusterSharding = ClusterSharding(system)
  ClusterSharding(system).init(
    Entity(MemberEntityKey)(entityContext => Member(entityContext.entityTypeKey.name, entityContext.entityId))
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  implicit val objectMapper: ObjectMapper = JacksonObjectMapperProvider(system).getOrCreate("jackson-cbor", None)
  applyObjectMapperMixins(objectMapper)

  //Do not use for RegisterMember
  private def extractEntityId(request: MemberRequest): String = {
    request match {
      case RegisterMember(_, _)             => None
      case ActivateMember(memberId, _)      => memberId.map(_.id)
      case InactivateMember(memberId, _)    => memberId.map(_.id)
      case SuspendMember(memberId, _)       => memberId.map(_.id)
      case TerminateMember(memberId, _)     => memberId.map(_.id)
      case UpdateMemberInfo(memberId, _, _) => memberId.map(_.id)
      case GetMemberInfo(memberId)          => memberId.map(_.id)
      case other                            => throw new RuntimeException(s"Unexpected request to extract id $other")
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
    //TODO check collision - for now ULID assumed to be unique - Entity will reject registerMember if already exists
    val memberId = ULID.newULIDString

    handleRequest(
      in,
      memberId,
      { case StatusReply.Success(MemberEventResponse(response @ MemberRegistered(_, _, _, _))) =>
        response
      }
    )
  }

  override def activateMember(in: ActivateMember): Future[MemberActivated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberActivated(_, _, _))) => response }
    )

  override def inactivateMember(
      in: InactivateMember
  ): Future[MemberInactivated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberInactivated(_, _, _))) => response }
    )

  override def suspendMember(in: SuspendMember): Future[MemberSuspended] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberSuspended(_, _, _))) => response }
    )

  override def terminateMember(in: TerminateMember): Future[MemberTerminated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberTerminated(_, _, _))) => response }
    )

  override def updateMemberInfo(
      in: UpdateMemberInfo
  ): Future[MemberInfoUpdated] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(MemberEventResponse(response @ MemberInfoUpdated(_, _, _, _))) => response }
    )
  override def getMemberInfo(in: GetMemberInfo): Future[MemberData] =
    handleRequest(
      in,
      extractEntityId(in),
      { case StatusReply.Success(response @ MemberData(_, _, _)) => response }
    )
}
