package com.improving.app.common

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OpenTelemetryTest extends AnyWordSpec with Matchers {

  "OpenTelemetry" must {
    "capture counters" in {
      System.setProperty("otel.java.global-autoconfigure.enabled","true")
      System.setProperty("otel.metrics.exporter", "logging")
      val cntr = OpenTelemetry.Counter("testCounter", "OpenTelemetryTest", "just a simple counter", "each")
      cntr.add(1L)
      cntr.add(10L)
      cntr.add(100L)
    }
    "capture histograms" in {
      val cntr = OpenTelemetry.Histogram("testHistogram", "OpenTelemetryTest", "just a simple histogram", "widgets/sec")
      cntr.record(1L)
      cntr.record(10L)
      cntr.record(100L)
    }
    "trace execution" in {
      System.setProperty("otel.java.global-autoconfigure.enabled", "true")
      System.setProperty("otel.metrics.exporter", "logging")
      val tracer = OpenTelemetry.Tracer("testTracer")
      val span = tracer.startSpan("top")
      for (i <- 1 to 10) {
        span.addEvent(i.toString)
      }
      span.end()
    }
  }
}