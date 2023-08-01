package com.improving.app.gateway.domain

import com.improving.app.store.domain.{EditableStoreInfo, StoreInfo, StoreMetaInfo, StoreStates}
import com.improving.app.gateway.domain.demoScenario.Store
import com.improving.app.gateway.domain.store.{
  EditableStoreInfo => GatewayEditableStoreInfo,
  StoreCreated,
  StoreInfo => GatewayStoreInfo,
  StoreMetaInfo => GatewayStoreMetaInfo,
  StoreStates => GatewayStoreStates
}

object storeUtil {

  implicit class StoreCreatedUtil(established: StoreCreated) {
    implicit def toStore: Store = Store(
      storeId = established.storeId,
      info = established.info.map(_.toInfo),
      metaInfo = established.metaInfo
    )
  }

  implicit class GatewayEditableStoreInfoUtil(info: GatewayEditableStoreInfo) {

    def toInfo: GatewayStoreInfo = GatewayStoreInfo(
      name = info.getName,
      description = info.getDescription,
      products = info.products,
      event = info.event,
      sponsoringOrg = info.sponsoringOrg
    )

    def toEditableInfo: EditableStoreInfo = EditableStoreInfo(
      name = info.name,
      description = info.description,
      products = info.products,
      event = info.event,
      sponsoringOrg = info.sponsoringOrg
    )
  }

  implicit class EditableStoreInfoUtil(info: EditableStoreInfo) {

    def toGatewayEditableInfo: GatewayEditableStoreInfo =
      GatewayEditableStoreInfo(
        name = info.name,
        description = info.description,
        products = info.products,
        event = info.event,
        sponsoringOrg = info.sponsoringOrg
      )
  }

  implicit class StoreInfoUtil(info: StoreInfo) {

    def toGatewayInfo: GatewayStoreInfo =
      GatewayStoreInfo(
        name = info.name,
        description = info.description,
        products = info.products,
        event = info.event,
        sponsoringOrg = info.sponsoringOrg
      )
  }

  implicit class StoreStateUtil(storeState: StoreStates) {
    def toGatewayStoreStates: GatewayStoreStates = {
      if (storeState.isStoreStatesDraft) GatewayStoreStates.STORE_STATES_DRAFT
      else if (storeState.isStoreStatesReady) GatewayStoreStates.STORE_STATES_READY
      else if (storeState.isStoreStatesOpen) GatewayStoreStates.STORE_STATES_OPEN
      else if (storeState.isStoreStatesClosed) GatewayStoreStates.STORE_STATES_CLOSED
      else GatewayStoreStates.STORE_STATES_DELETED
    }
  }

  implicit class StoreMetaUtil(meta: StoreMetaInfo) {
    def toGatewayStoreMeta: GatewayStoreMetaInfo = GatewayStoreMetaInfo(
      createdBy = meta.createdBy,
      createdOn = meta.createdOn,
      lastUpdatedBy = meta.lastUpdatedBy,
      lastUpdated = meta.lastUpdated
    )
  }
}
