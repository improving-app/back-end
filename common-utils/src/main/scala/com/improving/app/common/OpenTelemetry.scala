package com.improving.app.common

/*
 * `GlobalOpenTelemetry` singleton configured by OpenTelemetry Java agent, based environment variables or Java options
 */
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
    def createSpan(name: String): Span = tracer.spanBuilder(name).startSpan
  }
  case class Counter(name: String, contextName: String, description: String, uOfM: String) extends LongCounter {
    private val meter: Meter = sdk.meterBuilder(contextName).build()
    private val counter: LongCounter = meter
      .counterBuilder(name)
      .setDescription(description)
      .setUnit(uOfM)
      .build()

    override def add(value: Long): Unit = counter.add(value)

    override def add(value: Long, attributes: Attributes): Unit = counter.add(value, attributes)

    override def add(value: Long, attributes: Attributes, context: Context): Unit = counter.add(value, attributes, context)
  }
}
