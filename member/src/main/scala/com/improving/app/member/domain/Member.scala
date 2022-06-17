package com.improving.app.member.domain

import com.improving.app.member.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class Member(context: EventSourcedEntityContext) extends AbstractMember {
  override def emptyState: MemberState =
    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state")

  override def registerMember(currentState: MemberState, registerMember: api.RegisterMember): EventSourcedEntity.Effect[api.MemberRegistered] =
    effects.error("The command handler for `registerMember` is not implemented, yet")

  override def memberRegistered(currentState: MemberState, memberRegistered: api.MemberRegistered): MemberState =
    throw new RuntimeException("The event handler for `MemberRegistered` is not implemented, yet")

}
