package com.improving.app.product

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.improving.app.common.service.ServiceMain
import com.improving.app.product.api.{ProductServiceHandler, ProductServiceImpl}
import scala.concurrent.Future

/**
 * This is the running application for the Members project.
 */
object Main extends ServiceMain {
  override val projectName = "improving-app-product"
  override val port = 8085

  override def service(system: ActorSystem[Nothing]): HttpRequest => Future[HttpResponse] = {
    ProductServiceHandler.withServerReflection(new ProductServiceImpl()(system))(system)
  }

  run()
}
