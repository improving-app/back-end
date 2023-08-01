package com.improving.app.gatling.common

import com.google.`type`.calendar_period.CalendarPeriod.WEEK
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.io.Source
import scala.util.Random

object gen {
  val firstNamesFile = "firstNames.txt"
  val lastNamesFile = "lastNames.txt"
  val cityStatesFile = "fakariaCityStates.txt"
  val addressesFile = "addresses.txt"
  val eventsFile = "events.txt"

  val firstNames: Seq[String] = Source.fromResource(firstNamesFile).getLines().toSeq

  val lastNames: Seq[String] = Source.fromResource(lastNamesFile).getLines().toSeq

  val addresses: Seq[String] = Source.fromResource(addressesFile).getLines().toSeq

  val cityStates: Seq[Seq[String]] =
    Source.fromResource(cityStatesFile).getLines().toSeq.map {
      _.split(",").toSeq
    }

  val events: Seq[String] = Source.fromResource(eventsFile).getLines().toSeq
  def repeatListUntilNAndShuffle[T](n: Int, source: Seq[T]): Seq[T] = {
    var ret: Seq[T] = source
    for (i <- 0 to n by source.size) {
      ret ++= source
      println(i)
    }
    Random.shuffle(ret).take(n)
  }

  def genRandomStartAndEndAfterNow: (Timestamp, Timestamp) = {
    val instant = Instant
      .now()
      .truncatedTo(ChronoUnit.HOURS)
      .plus(Random.nextInt(70), ChronoUnit.DAYS)
      .plus(Random.nextInt(24), ChronoUnit.HOURS)
    (
      Timestamp.of(instant.getEpochSecond, instant.getNano),
      Timestamp.of(instant.getEpochSecond + 1800, instant.getNano)
    )
  }

  def genPhoneNumber: String =
    s"(${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)})-${Random.nextInt(10)}${Random.nextInt(10)}${Random
        .nextInt(10)}-${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}${Random.nextInt(10)}"

  def genPostalCode: String = Random.alphanumeric.toString().take(5)
}
