package com.improving.app.common

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OpenTelemetryTest extends AnyWordSpec with Matchers {

  "OpenTelemetry" must {
    "capture counters" in {
      System.setProperty("otel.java.global-autoconfigure.enabled","true")
      System.setProperty("otel.metrics.exporter", "logging")
      val cntr = OpenTelemetry.Counter("test", "OpenTelemetryTest", "just a simple counter", "each")
      cntr.add(1L)
      cntr.add(10L)
      cntr.add(100L)
    }
  }

}
