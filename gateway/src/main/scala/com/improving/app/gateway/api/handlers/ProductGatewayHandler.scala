package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.product.api.ProductServiceClient
import com.improving.app.product.domain.{ActivateProduct, CreateProduct, DeleteProduct}
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.product.{
  ActivateProduct => GatewayActivateProduct,
  AllSkus => GatewayAllSkus,
  CreateProduct => GatewayCreateProduct,
  DeleteProduct => GatewayDeleteProduct,
  ProductActivated,
  ProductCreated,
  ProductDeleted
}
import com.improving.app.gateway.domain.productUtil._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class ProductGatewayHandler(grpcClientSettingsOpt: Option[GrpcClientSettings] = None)(implicit
    val system: ActorSystem[_]
) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = getHostAndPortForService("product-service")

  private val productClient: ProductServiceClient = ProductServiceClient(
    grpcClientSettingsOpt.getOrElse(
      GrpcClientSettings
        .connectToServiceAt(clientHost, clientPort)
        .withTls(false)
    )
  )

  def createProduct(in: GatewayCreateProduct): Future[ProductCreated] =
    productClient
      .createProduct(
        CreateProduct(
          in.sku,
          in.info.map(_.toEditableInfo),
          in.onBehalfOf
        )
      )
      .map { response =>
        ProductCreated(
          response.sku,
          response.info.map(_.toGatewayEditableInfo),
          response.meta.map(_.toGatewayProductMeta)
        )
      }

  def activateProduct(in: GatewayActivateProduct): Future[ProductActivated] =
    productClient
      .activateProduct(
        ActivateProduct(
          in.sku,
          None,
          in.onBehalfOf
        )
      )
      .map { response =>
        ProductActivated(
          response.sku,
          response.info.map(_.toGatewayInfo),
          response.meta.map(_.toGatewayProductMeta)
        )
      }

  def deleteProduct(in: GatewayDeleteProduct): Future[ProductDeleted] =
    productClient
      .deleteProduct(
        DeleteProduct(
          in.sku,
          in.onBehalfOf
        )
      )
      .map { response =>
        ProductDeleted(
          response.sku,
          response.meta.map(_.toGatewayProductMeta)
        )
      }

  def getAllSkus: Future[GatewayAllSkus] =
    productClient.getAllSkus(com.google.protobuf.empty.Empty()).map(response => GatewayAllSkus(response.allSkus))
}
