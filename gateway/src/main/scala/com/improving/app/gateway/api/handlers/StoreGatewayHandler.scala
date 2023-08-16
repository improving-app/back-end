package com.improving.app.gateway.api.handlers

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.util.Timeout
import com.improving.app.store.domain.{CloseStore, CreateStore, DeleteStore, MakeStoreReady, OpenStore}
import com.improving.app.gateway.domain.common.util.getHostAndPortForService
import com.improving.app.gateway.domain.store.{
  AllStoreIds => GatewayAllStoreIds,
  CloseStore => GatewayCloseStore,
  CreateStore => GatewayCreateStore,
  DeleteStore => GatewayDeleteStore,
  MakeStoreReady => GatewayMakeStoreReady,
  OpenStore => GatewayOpenStore,
  StoreClosed,
  StoreCreated,
  StoreDeleted,
  StoreIsReady,
  StoreOpened
}
import com.improving.app.gateway.domain.storeUtil._
import com.improving.app.store.api.StoreServiceClient
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class StoreGatewayHandler(grpcClientSettingsOpt: Option[GrpcClientSettings] = None)(implicit
    val system: ActorSystem[_]
) extends StrictLogging {
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5 minute)

  protected val (clientHost, clientPort) = getHostAndPortForService("store-service")

  private val storeClient: StoreServiceClient = StoreServiceClient(
    grpcClientSettingsOpt.getOrElse(
      GrpcClientSettings
        .connectToServiceAt(clientHost, clientPort)
        .withTls(false)
    )
  )

  def createStore(in: GatewayCreateStore): Future[StoreCreated] =
    storeClient
      .createStore(
        CreateStore(
          in.storeId,
          in.onBehalfOf,
          in.info.map(_.toEditableInfo)
        )
      )
      .map { response =>
        StoreCreated(
          response.storeId,
          response.info.map(_.toGatewayEditableInfo),
          response.metaInfo.map(_.toGatewayStoreMeta)
        )
      }

  def readyStore(in: GatewayMakeStoreReady): Future[StoreIsReady] =
    storeClient
      .makeStoreReady(
        MakeStoreReady(
          in.storeId,
          in.onBehalfOf,
          in.info.map(_.toEditableInfo)
        )
      )
      .map { response =>
        StoreIsReady(
          response.storeId,
          response.info.map(_.toGatewayInfo),
          response.metaInfo.map(_.toGatewayStoreMeta)
        )
      }

  def openStore(in: GatewayOpenStore): Future[StoreOpened] =
    storeClient
      .openStore(
        OpenStore(
          in.storeId,
          in.onBehalfOf
        )
      )
      .map { response =>
        StoreOpened(
          response.storeId,
          response.info.map(_.toGatewayInfo),
          response.metaInfo.map(_.toGatewayStoreMeta)
        )
      }

  def closeStore(in: GatewayCloseStore): Future[StoreClosed] =
    storeClient
      .closeStore(
        CloseStore(
          in.storeId,
          in.onBehalfOf
        )
      )
      .map { response =>
        StoreClosed(
          response.storeId,
          response.info.map(_.toGatewayInfo),
          response.metaInfo.map(_.toGatewayStoreMeta)
        )
      }

  def deleteStore(in: GatewayDeleteStore): Future[StoreDeleted] =
    storeClient
      .deleteStore(
        DeleteStore(
          in.storeId,
          in.onBehalfOf
        )
      )
      .map { response =>
        StoreDeleted(
          response.storeId,
          response.info.map(_.toGatewayInfo),
          response.metaInfo.map(_.toGatewayStoreMeta)
        )
      }

  def getAllIds: Future[GatewayAllStoreIds] =
    storeClient.getAllIds(com.google.protobuf.empty.Empty()).map(response => GatewayAllStoreIds(response.allStoreIds))
}
