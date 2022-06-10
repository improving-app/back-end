package com.inventory

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

object Routes extends JsonFormats {

  private final val PersistenceIdDelimiter = "_"

  private def toEntityId(style: String, color: String, size: String) =
    s"$style$PersistenceIdDelimiter$color$PersistenceIdDelimiter$size"

  def routes(repo: LowInventoryRepository): Route = cors() {
    path("low-inventory") {
      parameters("style", "color", "size") { (style, color, size) =>
        get {
          complete(repo.find(toEntityId(style, color, size)))
        }
      } ~
      get {
        complete(repo.getAll())
      }
    }
  }
}
