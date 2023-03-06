package com.tenant

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.tenant.TestActor.{TestCommand, TestEvent}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TenantServiceImpl(sys: ActorSystem[TestActor.TestCommand]) extends TenantService {
  private implicit val system: ActorSystem[TestCommand] = sys
  println("created TenantServiceImpl")

  val testActor: ActorRef[TestCommand] = system
  override def testFunction(in: TestInput): Future[TestOutput] = {
    println("inside testFunction")
    implicit val timeout: Timeout = 3.seconds
    val result: Future[TestEvent] = testActor.ask(ref => TestCommand(in.myInput, ref))
    implicit val ec = system.executionContext
    result.map(result => TestOutput(result.whom))
  }
}
