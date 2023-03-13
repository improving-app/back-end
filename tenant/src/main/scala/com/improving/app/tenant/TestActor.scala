package com.improving.app.tenant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef

object TestActor {
  final case class TestCommand(whom: String, replyTo: ActorRef[TestEvent])
  final case class TestEvent(whom: String)

  def apply(): Behaviors.Receive[TestCommand] =
    Behaviors.receiveMessage {
      message =>
        message.replyTo ! TestEvent(s"hello ${message.whom}")
        Behaviors.same
    }
}