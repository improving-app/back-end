package com.improving.app.member.domain

import com.improving.app.member.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext

import java.time.Instant

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class Member(context: EventSourcedEntityContext) extends AbstractMember {
  override def emptyState: MemberState = MemberState.defaultInstance


  override def registerMember(currentState: MemberState, registerMember: api.RegisterMember): EventSourcedEntity.Effect[api.MemberRegistered] =
    if (currentState == emptyState) {
      val meta = api.MemberMetaInfo(createdOn = Instant.now().toEpochMilli, createdBy = registerMember.registeringMember, memberState = api.MemberState.Active)
      val event = for {
        memToAdd <- registerMember.memberToAdd
      }yield api.MemberRegistered(memToAdd.memberId, memToAdd.memberInfo, Some(meta))
      if (event.isDefined)
        effects.emitEvent(event.get).thenReply(_ => event.get)
      else
        effects.error(s"Member ${registerMember.memberToAdd.map(x => x.memberId).getOrElse("missing member Id")} is missing data")
    } else {
      effects.error(s"Member ${registerMember.memberToAdd.map(x => x.memberId).getOrElse("missing member Id")} has already been registered")
    }

    //effects.error("The command handler for `registerMember` is not implemented, yet")

  override def memberRegistered(currentState: MemberState, memberRegistered: api.MemberRegistered): MemberState =
    currentState.copy(memberId = memberRegistered.memberId, memberInfo = memberRegistered.memberInfo, memberMetaInfo = memberRegistered.memberMetaInfo)

}
