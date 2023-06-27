package com.improving.app.organization

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.organization.api.{OrganizationServiceHandler, OrganizationServiceImpl}

import scala.concurrent.Future

/**
 * This is the running application for the Organization project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-organization"
  override val port = 8082
  override def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] =
    OrganizationServiceHandler.withServerReflection(new OrganizationServiceImpl(system))(system)

  run()
}
