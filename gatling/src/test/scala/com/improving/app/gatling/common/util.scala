package com.improving.app.gatling.common

import scala.util.Random

object util {

  def genPhoneNumber: String =
    s"(${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)})-${Random.nextInt(10)}${Random.nextInt(10)}${Random
        .nextInt(10)}-${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}"

  def genPostalCode: String = Random.alphanumeric.toString().take(5)
}
