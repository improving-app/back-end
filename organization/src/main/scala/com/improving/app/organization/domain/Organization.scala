package com.improving.app.organization.domain

import com.improving.app.organization.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext

import java.time.Instant

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class Organization(context: EventSourcedEntityContext) extends AbstractOrganization {

  override def emptyState: OrgState = OrgState.defaultInstance


  override def establishOrganization(
    currentState: OrgState,
    establishOrganization: api.EstablishOrganization
  ): EventSourcedEntity.Effect[api.OrganizationEstablished] = {
    if (currentState == emptyState) {
      val timestamp: Long = Instant.now().toEpochMilli
      val event = api.OrganizationEstablished(establishOrganization.orgId, establishOrganization.info,timestamp)
      effects.emitEvent(event).thenReply(_ => event)
    } else {
      effects.error(s"Organization ${establishOrganization.orgId} has already been established")
    }
  }

  override def organizationEstablished(
    currentState: OrgState,
    organizationEstablished: api.OrganizationEstablished
  ): OrgState = {
    currentState.copy(
      orgId = organizationEstablished.orgId,
      info = organizationEstablished.info,
      timestamp = organizationEstablished.timestamp
    )
  }

}
