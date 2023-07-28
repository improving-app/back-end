package com.improving.app.gatling.common

import scala.io.Source
import scala.util.Random

object gen {
  val firstNamesFile = "firstNames.txt"
  val lastNamesFile = "lastNames.txt"
  val cityStatesFile = "fakariaCityStates.txt"
  val addressesFile = "addresses.txt"

  val firstNames: Seq[String] = Source.fromResource(firstNamesFile).getLines().toSeq

  val lastNames: Seq[String] = Source.fromResource(lastNamesFile).getLines().toSeq

  val addresses: Seq[String] = Source.fromResource(addressesFile).getLines().toSeq

  val cityStates: Seq[Seq[String]] =
    Source.fromResource(cityStatesFile).getLines().toSeq.map {
      _.split(",").toSeq
    }

  def genPhoneNumber: String =
    s"(${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)})-${Random.nextInt(10)}${Random.nextInt(10)}${Random
        .nextInt(10)}-${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}"

  def genPostalCode: String = Random.alphanumeric.toString().take(5)
}
