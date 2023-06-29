package com.improving.app.gateway.domain.common

import com.typesafe.config.ConfigFactory

import scala.util.Random

object util {

  def genPhoneNumber: String =
    s"(${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)})-${Random.nextInt(10)}${Random.nextInt(10)}${Random
        .nextInt(10)}-${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}"

  def genPostalCode: String = Random.alphanumeric.toString().take(5)

  def getHostAndPortForService(serviceName: String): (String, Int) = {
    val config = ConfigFactory
      .load("application.conf")
      .withFallback(ConfigFactory.defaultApplication())
    (
      config.getString(s"services.$serviceName.host"),
      config.getInt(s"services.$serviceName.port")
    )
  }
}
