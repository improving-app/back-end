package com.yoppworks.inventory

import scala.concurrent.Future

import akka.actor.ActorSystem
import org.slf4j.LoggerFactory

/**
 * Example customer health check.
 * Would need to update config to enable this.
 */
class HealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)

  override def apply(): Future[Boolean] = {
    Future.successful(true)
  }
}
