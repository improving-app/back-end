package com.improving.app.member

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.member.api.{MemberServiceHandler, MemberServiceImpl}
import scala.concurrent.Future

/**
 * This is the running application for the Members project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-member"
  override val port = 8081

  override def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] = {
    MemberServiceHandler.withServerReflection(new MemberServiceImpl(system))(system)
  }

  run()
}
