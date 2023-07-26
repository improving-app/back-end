package com.improving.app.common

import io.opentelemetry.api
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{LongCounter, Meter}
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context

object OpenTelemetry {
  private lazy val sdk: api.OpenTelemetry = GlobalOpenTelemetry.get()

  case class Tracer(scope: String) {
    private val tracer: api.trace.Tracer = sdk.getTracer(scope)
    def startSpan(name: String): Span = tracer.spanBuilder(name).startSpan
  }

  case class Counter(name: String, contextName: String, description: String, uOfM: String) extends LongCounter {
    private val meter: Meter = sdk.meterBuilder(contextName).build()
    private val counter: LongCounter = meter
      .counterBuilder(name)
      .setDescription(description)
      .setUnit(uOfM)
      .build()

    override def add(value: Long): Unit = counter.add(value)

    override def add(value: Long, attributes: Attributes): Unit =
      counter.add(value, attributes)

    override def add(value: Long, attributes: Attributes, context: Context): Unit =
      counter.add(value, attributes, context)
  }

  case class Histogram(name: String, contextName: String, description: String, unit: String) extends api.metrics.LongHistogram {
    private val meter: api.metrics.Meter = sdk.getMeter(contextName)
    private val histogram: api.metrics.LongHistogram =
      meter.histogramBuilder(name).ofLongs.setDescription(description).setUnit(unit).build()

    override def record(value: Long): Unit = histogram.record(value)

    override def record(value: Long, attributes: Attributes): Unit =
      histogram.record(value, attributes)

    override def record(value: Long, attributes: Attributes, context: Context): Unit =
      histogram.record(value, attributes, context)
  }
}
