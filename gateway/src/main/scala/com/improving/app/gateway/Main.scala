package com.improving.app.gateway

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.gateway.api.MemberGatewayServiceImpl
import com.improving.app.tenant.api.MemberGatewayServiceHandler

import scala.concurrent.Future

/**
 * This is the running application for the Members project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-member"
  override val port = 8081

  override protected def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] =
    MemberGatewayServiceHandler.withServerReflection(new MemberGatewayServiceImpl()(system))(system)

  run()
}
