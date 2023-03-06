package com.improving.app.organization.domain
// import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
// import kalix.scalasdk.testkit.EventSourcedResult
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// This class was initially generated based on the .proto definition by Kalix tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class OrganizationSpec extends AnyWordSpec with Matchers {
  /*  "The Organization" should {
    "allow being established" in {
      val testKit = OrganizationTestKit(new Organization(_))
      val command = EstablishOrganization(
        orgId = Some(OrganizationId(UUID.randomUUID().toString)),
        info = Some(OrganizationInfo("test", "T", None))
      )
      val result = testKit.establishOrganization(command)
      result.events.isEmpty shouldBe false
      result.didEmitEvents shouldBe true
      result.isError shouldBe false
      val actualEvent = result.nextEvent[OrganizationEstablished]
      actualEvent.orgId shouldBe command.orgId
      actualEvent.info shouldBe command.info
      actualEvent.timestamp > 0 shouldBe true
      testKit.currentState shouldBe
        OrgState(actualEvent.orgId, actualEvent.info, actualEvent.timestamp)
      result.reply.orgId shouldBe command.orgId
      result.reply.info shouldBe command.info
      result.reply.timestamp shouldBe actualEvent.timestamp
    }
  }*/
}
