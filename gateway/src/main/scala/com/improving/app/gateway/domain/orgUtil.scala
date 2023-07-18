package com.improving.app.gateway.domain

import com.improving.app.common.domain.util.EditableAddressUtil
import com.improving.app.gateway.domain.demoScenario.Organization
import com.improving.app.gateway.domain.organization.{OrganizationEstablished, OrganizationInfo, EditableOrganizationInfo => GatewayEditableOrganizationInfo, OrganizationMetaInfo => GatewayOrganizationMetaInfo, OrganizationStates => GatewayOrganizationStates}
import com.improving.app.organization.domain.{EditableOrganizationInfo, OrganizationMetaInfo}

object orgUtil {

  implicit class EstablishedOrganizationUtil(established: OrganizationEstablished) {
    implicit def toOrganization: Organization = Organization(
      organizationId = established.organizationId,
      organizationInfo = established.organizationInfo.map(_.toInfo),
      metaInfo = established.metaInfo
    )
  }

  implicit class GatewayEditableOrganizationInfoUtil(info: GatewayEditableOrganizationInfo) {
    implicit def toInfo: OrganizationInfo = OrganizationInfo(
      name = info.getName,
      shortName = info.shortName,
      tenant = info.tenant,
      isPublic = info.getIsPublic,
      address = info.address.map(_.toAddress),
      url = info.url,
      logo = info.logo
    )

    implicit def toEditable: EditableOrganizationInfo = EditableOrganizationInfo(
      info.name,
      info.shortName,
      info.tenant,
      info.isPublic,
      info.address,
      info.url,
      info.logo
    )
  }

  implicit class EditableOrganizationInfoUtil(info: EditableOrganizationInfo) {

    def toGatewayEditable: GatewayEditableOrganizationInfo =
      GatewayEditableOrganizationInfo(
        info.name,
        info.shortName,
        info.tenant,
        info.isPublic,
        info.address,
        info.url,
        info.logo
      )
  }

  implicit class OrganizationMetaInfoUtil(metaInfo: OrganizationMetaInfo) {

    def toGatewayMeta: GatewayOrganizationMetaInfo = GatewayOrganizationMetaInfo(
      metaInfo.createdOn,
      metaInfo.createdBy,
      metaInfo.lastUpdated,
      metaInfo.lastUpdatedBy,
      GatewayOrganizationStates.fromValue(metaInfo.state.value)
    )
  }
}
