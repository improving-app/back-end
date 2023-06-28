package com.improving.app.store

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.store.api.StoreServiceHandler

import scala.concurrent.Future

/**
 * This is the running application for the Organization project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-store"
  override val port = 8083
  override def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] =
    StoreServiceHandler.withServerReflection(new StoreServiceImpl(system))(system)

  run()
}
