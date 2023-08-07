package com.improving.app.store

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.grpc.GrpcServiceException
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.google.rpc.Code
import com.google.rpc.error_details.LocalizedMessage
import com.improving.app.common.OpenTelemetry
import com.improving.app.common.errors.ValidationError
import com.improving.app.store.api.StoreService
import com.improving.app.store.domain.{AddProductsToStore, CloseStore, CreateStore, DeleteStore, EditStoreInfo, MakeStoreReady, OpenStore, ProductsAddedToStore, ProductsRemovedFromStore, RemoveProductsFromStore, Store, StoreClosed, StoreCommand, StoreCreated, StoreDeleted, StoreEventMessage, StoreInfoEdited, StoreIsReady, StoreOpened, StoreRequestPB, StoreTerminated, TerminateStore}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt

class StoreServiceImpl(sys: ActorSystem[_]) extends StoreService {

  private val tracer: OpenTelemetry.Tracer = OpenTelemetry.Tracer("StoreServiceImpl")
  implicit private val system: ActorSystem[_] = sys
  implicit val timeout: Timeout = 5.minutes
  implicit val executor: ExecutionContextExecutor = system.executionContext

  val sharding: ClusterSharding = ClusterSharding(system)

  sharding.init(
    Entity(Store.TypeKey)(
      createBehavior = entityContext =>
        Store(
          PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
        )
    )
  )

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

  private def handleRequest(
      in: StoreRequestPB with StoreCommand,
  ): Future[StoreEventMessage.SealedValue] = {
    val span = tracer.startSpan("handleRequest")
    try {
      in.storeId
        .map { id =>
          val result = sharding
            .entityRefFor(Store.TypeKey, id.id)
            .ask(ref => Store.StoreRequestEnvelope(in, ref))
          result.transform(result => result.getValue.asMessage.sealedValue, exception => exceptionHandler(exception))
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
    } finally span.end()
  }

  override def createStore(in: CreateStore): Future[StoreCreated] = handleRequest(in).map(_.storeCreated.get)

  override def makeStoreReady(in: MakeStoreReady): Future[StoreIsReady] = handleRequest(in).map(_.storeIsReady.get)

  override def deleteStore(in: DeleteStore): Future[StoreDeleted] = handleRequest(in).map(_.storeDeleted.get)

  override def openStore(in: OpenStore): Future[StoreOpened] = handleRequest(in).map(_.storeOpened.get)

  override def closeStore(in: CloseStore): Future[StoreClosed] = handleRequest(in).map(_.storeClosed.get)

  override def terminateStore(in: TerminateStore): Future[StoreTerminated] =
    handleRequest(in).map(_.storeTerminated.get)

  override def editStoreInfo(in: EditStoreInfo): Future[StoreInfoEdited] = handleRequest(in).map(_.storeInfoEdited.get)

  override def addProductsToStore(in: AddProductsToStore): Future[ProductsAddedToStore] =
    handleRequest(in).map(_.productsAddedToStore.get)

  override def removeProductsFromStore(in: RemoveProductsFromStore): Future[ProductsRemovedFromStore] =
    handleRequest(in).map(_.productsRemovedFromStore.get)
}
