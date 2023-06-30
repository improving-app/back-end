package com.improving.app.gateway.domain.common

import com.improving.app.common.domain.util.{EditableAddressUtil, EditableContactUtil}
import com.improving.app.gateway.domain.demoScenario.Tenant
import com.improving.app.gateway.domain.tenant.{
  EditableTenantInfo => GatewayEditableTenantInfo,
  TenantEstablished,
  TenantInfo,
  TenantMetaInfo => GatewayTenantMetaInfo,
  TenantOrganizationList => GatewayTenantOrganizationList,
  TenantStates => GatewayTenantStates
}
import com.improving.app.tenant.domain.{EditableTenantInfo, TenantMetaInfo, TenantOrganizationList}

object tenantUtil {

  implicit class EstablishedTenantUtil(established: TenantEstablished) {
    implicit def toTenant: Tenant = Tenant(
      tenantId = established.tenantId,
      tenantInfo = established.tenantInfo.map(_.toInfo),
      metaInfo = established.metaInfo
    )
  }

  implicit class GatewayEditableTenantInfoUtil(info: GatewayEditableTenantInfo) {
    implicit def toInfo: TenantInfo = TenantInfo(
      name = info.getName,
      primaryContact = info.primaryContact.map(_.toContact),
      address = info.address.map(_.toAddress),
      organizations = info.organizations
    )
  }

  def gatewayEditableTenantInfoToEditableInfo(info: GatewayEditableTenantInfo): EditableTenantInfo = EditableTenantInfo(
    info.name,
    info.primaryContact,
    info.address,
    info.organizations.map(list => TenantOrganizationList(list.value)),
  )

  def editableTenantInfoToGatewayEditableInfo(info: EditableTenantInfo): GatewayEditableTenantInfo =
    GatewayEditableTenantInfo(
      info.name,
      info.primaryContact,
      info.address,
      info.organizations.map(list => GatewayTenantOrganizationList(list.value)),
    )

  def tenantMetaToGatewayTenantMeta(metaInfo: TenantMetaInfo): GatewayTenantMetaInfo = GatewayTenantMetaInfo(
    metaInfo.createdOn,
    metaInfo.createdBy,
    metaInfo.lastUpdated,
    metaInfo.lastUpdatedBy,
    GatewayTenantStates.fromValue(metaInfo.state.value)
  )
}
