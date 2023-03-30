package com.improving.app.organization.domain

import com.improving.app.common.domain.MemberId

trait HasActingMember {
  def actingMember: Option[MemberId]
}
