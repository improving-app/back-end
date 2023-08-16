package com.improving.app.product.api

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
import com.improving.app.common.domain.Sku
import com.improving.app.product.domain.{ProductResponse, _}
import com.improving.app.product.domain.Product.{ProductEntityKey, ProductEnvelope}

import scala.:+
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{existentials, postfixOps}

class ProductServiceImpl()(implicit val system: ActorSystem[_]) extends ProductService {

  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  // Create a new product
  val sharding: ClusterSharding = ClusterSharding(system)
  ClusterSharding(system).init(
    Entity(ProductEntityKey)(entityContext => Product(entityContext.entityTypeKey.name, entityContext.entityId))
  )

  Cluster(system).manager ! Join(Cluster(system).selfMember.address)

  private def handleResponse[T](
      eventHandler: PartialFunction[StatusReply[ProductResponse], T]
  ): PartialFunction[StatusReply[ProductResponse], T] = {
    eventHandler.orElse {
      case StatusReply.Success(response) => throw new RuntimeException(s"Unexpected response $response")
      case StatusReply.Error(ex)         => throw ex
    }
  }

  private def handleCommand[T](
      in: ProductRequestPB with ProductCommand,
      productHandler: PartialFunction[StatusReply[ProductResponse], T]
  ): Future[T] = in.sku
    .map { id =>
      val productEntity = sharding.entityRefFor(ProductEntityKey, id.sku)

      productEntity
        .ask[StatusReply[ProductResponse]](replyTo => ProductEnvelope(in, replyTo))
        .map { handleResponse(productHandler) }
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
      in: ProductRequestPB with ProductQuery,
      productHandler: PartialFunction[StatusReply[ProductResponse], T]
  ): Future[T] = in.sku
    .map { sku =>
      val productEntity = sharding.entityRefFor(ProductEntityKey, sku.sku)

      productEntity
        .ask[StatusReply[ProductResponse]](replyTo => ProductEnvelope(in, replyTo))
        .map { handleResponse(productHandler) }
    }
    .getOrElse(
      Future.failed(
        GrpcServiceException.create(
          Code.INVALID_ARGUMENT,
          "An entity Id (sku) was not provided",
          java.util.List.of(in.asMessage)
        )
      )
    )

  /**
   * post: "product/{productId}/"
   */
  override def editProductInfo(in: EditProductInfo): Future[ProductInfoEdited] =
    handleCommand(
      in,
      { case StatusReply.Success(ProductEventResponse(response @ ProductInfoEdited(_, _, _, _), _)) =>
        response
      }
    )

  /**
   * post: "product/{productId}/create/"
   */
  override def createProduct(in: CreateProduct): Future[ProductCreated] = handleCommand(
    in,
    { case StatusReply.Success(ProductEventResponse(response @ ProductCreated(_, _, _, _), _)) =>
      response
    }
  )

  /**
   * post: "product/{productId}/activate/"
   */
  override def activateProduct(in: ActivateProduct): Future[ProductActivated] = handleCommand(
    in,
    {
      case StatusReply.Success(
            ProductEventResponse(response @ ProductActivated(_, _, _, _), _)
          ) =>
        response
    }
  )

  /**
   * post: "product/{productId}/inactivate/"
   */
  override def inactivateProduct(in: InactivateProduct): Future[ProductInactivated] = handleCommand(
    in,
    { case StatusReply.Success(ProductEventResponse(response @ ProductInactivated(_, _, _, _), _)) =>
      response
    }
  )

  /**
   * get:"product/{productId}/end"
   */
  override def getProductInfo(in: GetProductInfo): Future[ProductData] = handleQuery(
    in,
    { case StatusReply.Success(response @ ProductData(_, _, _, _)) =>
      response
    }
  )

  override def deleteProduct(in: DeleteProduct): Future[ProductDeleted] = handleCommand(
    in,
    { case StatusReply.Success(ProductEventResponse(response @ ProductDeleted(_, _, _), _)) =>
      response
    }
  )

  override def getAllSkus(in: Empty): Future[AllSkus] = {
    val readJournal =
      PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    readJournal.currentPersistenceIds().runFold(Seq[Sku]())(_ :+ Sku(_)).map { seq =>
      AllSkus(seq)
    }
  }
}
