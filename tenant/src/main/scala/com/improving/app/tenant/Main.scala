package com.improving.app.tenant

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.tenant.api.TenantServiceHandler
import scala.concurrent.Future

/**
 * This is the running application for the Tenant project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-tenant"
  override val port = 8080
  override def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] =
    TenantServiceHandler.withServerReflection(new TenantServiceImpl(system))(system)

  run()
}
