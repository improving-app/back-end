package com.improving.app.store

import akka.grpc.GrpcClientSettings
import com.improving.app.common.test.ServiceTestContainerSpec
import com.improving.app.store.api.StoreService
import com.improving.app.store.api.StoreServiceClient
import com.improving.app.store.domain.{CreateStore, MakeStoreReady, StoreStates}
import com.improving.app.store.domain._
import com.improving.app.store.domain.TestData.baseStoreInfo
import com.improving.app.common.domain._
import org.scalatest.tagobjects.Retryable


import scala.util.Random

class StoreServerSpec extends ServiceTestContainerSpec(8083, "store-service") {

  val storeId = Random.nextString(31)
  val storeInfo: EditableStoreInfo
  = EditableStoreInfo(
    Some(baseStoreInfo.getInfo.name),
    Some(baseStoreInfo.getInfo.description),
    baseStoreInfo.getInfo.sponsoringOrg
  )

  private def getClient(containers: Containers): StoreService = {
    val (host, port) = getContainerHostPort(containers)
    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)
    StoreServiceClient(clientSettings)
  }

  behavior.of("TestServer in a test container")

  override def afterAll(): Unit = {
    system.terminate()
  }

  it should "expose a port for store-service" in {
    withContainers { containers =>
      validateExposedPort(containers)
    }
  }

  it should "gracefully handle bad requests that fail at service level" taggedAs Retryable in {
    withContainers { containers => }
  }


  it should "properly process createStore" taggedAs Retryable in {

    withContainers { containers =>
      val client = getClient(containers)
      val createdResponse = client
        .createStore(
          CreateStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("creatingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      createdResponse.storeId shouldBe Some(StoreId(storeId))
    }
  }

  it should "properly mark the store as ready" taggedAs Retryable in {

    withContainers { containers =>
      val client = getClient(containers)
      val createdResponse = client
        .createStore(
          CreateStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("creatingUser")),
            info =  Some(storeInfo)
          )
        ).futureValue

      val storeReadyResponse = client
        .makeStoreReady(
          MakeStoreReady(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("readyingUser")),
            info =  Some(storeInfo)
          )
        ).futureValue

      val storeReadyMeta: StoreMetaInfo = storeReadyResponse.getMetaInfo
      storeReadyMeta.getCreatedBy shouldEqual MemberId("creatingUser")
      storeReadyMeta.getLastUpdatedBy shouldEqual MemberId("readyingUser")
    }

  }

  it should "properly open the store" taggedAs Retryable in {

    withContainers { containers =>
      val client = getClient(containers)
      val createdResponse = client
        .createStore(
          CreateStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("creatingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      val storeReadyResponse = client
        .makeStoreReady(
          MakeStoreReady(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("readyingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      val openStoreResponse = client
        .openStore(
          OpenStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("openingUser")),
          )
        ).futureValue

      val storeOpenMeta: StoreMetaInfo = openStoreResponse.getMetaInfo

      storeOpenMeta.getCreatedBy shouldEqual MemberId("creatingUser")
      storeOpenMeta.getLastUpdatedBy shouldEqual MemberId("openingUser")
    }
  }

  it should "properly close the store" taggedAs Retryable in {

    withContainers { containers =>
      val client = getClient(containers)
      val createdResponse = client
        .createStore(
          CreateStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("creatingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      val storeReadyResponse = client
        .makeStoreReady(
          MakeStoreReady(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("readyingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      val openStoreResponse = client
        .openStore(
          OpenStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("openingUser"))
          )
        ).futureValue

      val closeStoreResponse = client
        .closeStore(
          CloseStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("closingUser"))
          )
        ).futureValue

      val closeStoreMeta: StoreMetaInfo = closeStoreResponse.getMetaInfo

      closeStoreMeta.getCreatedBy shouldEqual MemberId("creatingUser")
      closeStoreMeta.getLastUpdatedBy shouldEqual MemberId("closingUser")
    }
  }
  it should "properly terminate the store" taggedAs Retryable in {

    withContainers { containers =>
      val client = getClient(containers)
      val createdResponse = client
        .createStore(
          CreateStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("creatingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      val storeReadyResponse = client
        .makeStoreReady(
          MakeStoreReady(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("readyingUser")),
            info = Some(storeInfo)
          )
        ).futureValue

      val openStoreResponse = client
        .openStore(
          OpenStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("openingUser"))
          )
        ).futureValue

      val closeStoreResponse = client
        .closeStore(
          CloseStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("closingUser"))
          )
        ).futureValue

      val terminateStoreResponse = client
        .terminateStore(
          TerminateStore(
            storeId = Some(StoreId(storeId)),
            onBehalfOf = Some(MemberId("terminatingUser"))
          )
        ).futureValue

      val terminateStoreMeta: StoreMetaInfo = terminateStoreResponse.getMetaInfo

      terminateStoreMeta.getCreatedBy shouldEqual MemberId("creatingUser")
      terminateStoreMeta.getLastUpdatedBy shouldEqual MemberId("terminatingUser")
    }
  }
}