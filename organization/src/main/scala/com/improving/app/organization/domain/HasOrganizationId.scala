package com.improving.app.organization.domain

import com.improving.app.common.domain.OrganizationId

trait HasOrganizationId {
  def orgId: Option[OrganizationId]

  def extractOrganizationId: String =
    orgId match {
      case Some(OrganizationId(id, _)) => id
      case other =>
        throw new RuntimeException(s"Unexpected request to extract id $other")
    }
}
