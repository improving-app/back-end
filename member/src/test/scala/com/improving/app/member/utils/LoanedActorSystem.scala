package com.improving.app.member.utils

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory

import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

trait LoanedActorSystem {
  def configOverrides: Map[String, Any] = Map.empty

  private val n = new AtomicInteger

  def withActorSystem[T](testCode: ActorSystem => T): T = {
    val system = ActorSystem(
      s"${this.getClass.getSimpleName}-${n.incrementAndGet()}",
      ConfigFactory
        .parseMap(configOverrides.asJava)
        .withFallback(ConfigFactory.load())
    )
    try {
      testCode(system)
    } finally {
      TestKit.shutdownActorSystem(system)
    }
  }
}
