package com.inventory

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait JsonFormats extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val lowInventoryFormat: RootJsonFormat[LowInventory] = jsonFormat5(LowInventory)
}
