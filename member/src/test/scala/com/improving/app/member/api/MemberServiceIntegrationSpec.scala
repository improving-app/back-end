package com.improving.app.member.api

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec



class MemberServiceIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  // implicit private val patience: PatienceConfig =
  //   PatienceConfig(Span(5, Seconds), Span(500, Millis))

  // private val testKit = KalixTestKit(Main.createKalix()).start()

  // private val client = testKit.getGrpcClient(classOf[MemberService])

  "MemberService" must {

    "have example test that can be removed" in {
      true
      // use the gRPC client to send requests to the
      // proxy and verify the results
    }

  }

  override def afterAll(): Unit = {
   // testKit.stop()
    super.afterAll()
  }
}
