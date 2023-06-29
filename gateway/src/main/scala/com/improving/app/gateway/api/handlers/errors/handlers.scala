package com.improving.app.gateway.api.handlers.errors

import akka.grpc.GrpcServiceException
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import io.circe.Json

object handlers {
  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler { case e: GrpcServiceException =>
      complete(
        HttpResponse(
          BadRequest,
          entity = HttpEntity(ContentTypes.`application/json`, Json.fromString(e.getMessage).toString())
        )
      )
    }
}
