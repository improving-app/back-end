package com.tenant

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TestActorSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers{
  val testKit = ActorTestKit()
  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A TestActor" must {
    "properly process a TestCommand" in {
      val p = testKit.spawn(TestActor())
      val probe = testKit.createTestProbe[TestActor.TestEvent]()
      p ! TestActor.TestCommand("myTest", probe.ref)
      probe.expectMessage(TestActor.TestEvent("hello myTest"))
    }
  }
}
