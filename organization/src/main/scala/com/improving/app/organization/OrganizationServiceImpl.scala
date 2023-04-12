package com.improving.app.organization

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.common.errors.ValidationError
import com.improving.app.organization.api.OrganizationService
import com.improving.app.organization.domain.OrganizationValidation.organizationRequestValidator
import com.improving.app.organization.domain.{ActivateOrganization, EstablishOrganization, GetOrganizationInfo, Organization, OrganizationActivated, OrganizationEstablished, OrganizationInfo, OrganizationSuspended, OrganizationTerminated, SuspendOrganization, TerminateOrganization}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OrganizationServiceImpl(sys: ActorSystem[_]) extends OrganizationService {
  private implicit val system: ActorSystem[_] = sys
  implicit val timeout: Timeout = 5.minute
  implicit val executor = system.executionContext

  val sharding = ClusterSharding(system)

  case class BadRequest(message: String) extends Exception

  sharding.init(Entity(Organization.TypeKey)(
    createBehavior = entityContext =>
      Organization(
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
      )
  ))

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  private def exceptionHandler(exception: Throwable): GrpcServiceException = {
    GrpcServiceException(
      code = Code.INVALID_ARGUMENT,
      message = exception.getMessage,
      details = Seq(new LocalizedMessage("EN", exception.getMessage))
    )
  }

  private def validationFailure(validationError: ValidationError): GrpcServiceException = {
    GrpcServiceException(
      code = Code.INVALID_ARGUMENT,
      message = validationError.message,
      details = Seq(new LocalizedMessage("EN", validationError.message))
    )
  }

  override def establishOrganization(in: EstablishOrganization): Future[OrganizationEstablished] = {
    organizationRequestValidator(in) match {
      case Some(error) => Future.failed(validationFailure(error))
      case None =>
        val result = sharding.entityRefFor(Organization.TypeKey, in.organizationId.get.id)
          .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
        result.transform(
          result => result.getValue.asMessage.sealedValue.organizationEstablished.get,
          exception => exceptionHandler(exception)
        )
    }
  }

  override def activateOrganization(in: ActivateOrganization): Future[OrganizationActivated] = {
    organizationRequestValidator(in) match {
      case Some(error) => Future.failed(validationFailure(error))
      case None =>
        val result = sharding.entityRefFor(Organization.TypeKey, in.organizationId.get.id)
          .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
        result.transform(
          result => result.getValue.asMessage.sealedValue.organizationActivated.get,
          exception => exceptionHandler(exception)
        )
    }
  }

  override def suspendOrganization(in: SuspendOrganization): Future[OrganizationSuspended] = {
    organizationRequestValidator(in) match {
      case Some(error) => Future.failed(validationFailure(error))
      case None =>
        val result = sharding.entityRefFor(Organization.TypeKey, in.organizationId.get.id)
          .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
        result.transform(
          result => result.getValue.asMessage.sealedValue.organizationSuspended.get,
          exception => exceptionHandler(exception)
        )
    }
  }

  override def terminateOrganization(in: TerminateOrganization): Future[OrganizationTerminated] = {
    organizationRequestValidator(in) match {
      case Some(error) => Future.failed(validationFailure(error))
      case None =>
        val result = sharding.entityRefFor(Organization.TypeKey, in.organizationId.get.id)
          .ask(ref => Organization.OrganizationRequestEnvelope(in, ref))
        result.transform(
          result => result.getValue.asMessage.sealedValue.organizationTerminated.get,
          exception => exceptionHandler(exception)
        )
    }
  }

  override def getOrganizationInfo(in: GetOrganizationInfo): Future[OrganizationInfo] = ???
}
