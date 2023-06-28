package com.improving.app.gateway.domain.common

import com.improving.app.gateway.domain.tenant.{
  EditableTenantInfo => GatewayEditableTenantInfo,
  TenantMetaInfo => GatewayTenantMetaInfo,
  TenantOrganizationList => GatewayTenantOrganizationList,
  TenantStates => GatewayTenantStates
}
import com.improving.app.tenant.domain.{EditableTenantInfo, TenantMetaInfo, TenantOrganizationList}

object tenantUtil {
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
