package com.improving.app.organization.domain

import com.improving.app.organization.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class Organization(context: EventSourcedEntityContext) extends AbstractOrganization {
  override def emptyState: OrgState =
    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state")

  override def establishOrganization(currentState: OrgState, establishOrganization: api.EstablishOrganization): EventSourcedEntity.Effect[api.OrganizationEstablished] =
    effects.error("The command handler for `establishOrganization` is not implemented, yet")

  override def organizationEstablished(currentState: OrgState, organizationEstablished: api.OrganizationEstablished): OrgState =
    throw new RuntimeException("The event handler for `OrganizationEstablished` is not implemented, yet")

}
