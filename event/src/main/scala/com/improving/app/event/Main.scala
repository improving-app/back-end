package com.improving.app.event

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.event.api.{EventServiceHandler, EventServiceImpl}

import scala.concurrent.Future

/**
 * This is the running application for the Members project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-event"
  override val port = 8084

  override def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] = {
    EventServiceHandler.withServerReflection(new EventServiceImpl()(system))(system)
  }

  run()
}
