package com.improving.app.member.domain

import com.improving.app.member.api
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.testkit.EventSourcedResult
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class MemberSpec extends AnyWordSpec with Matchers {
  "The Member" should {
    "have example test that can be removed" in {
      val testKit = MemberTestKit(new Member(_))
      pending
      // use the testkit to execute a command:
      // val result: EventSourcedResult[R] = testKit.someOperation(SomeRequest("id"));
      // verify the emitted events
      // val actualEvent: ExpectedEvent = result.nextEventOfType[ExpectedEvent]
      // actualEvent shouldBe expectedEvent
      // verify the final state after applying the events
      // testKit.state() shouldBe expectedState
      // verify the reply
      // result.reply shouldBe expectedReply
      // verify the final state after the command
    }

    "correctly process commands of type registerMember" in {
      val testKit = MemberTestKit(new Member(_))
      pending
      // val result: EventSourcedResult[api.MemberRegistered] = testKit.registerMember(api.RegisterMember(...))
    }
  }
}
