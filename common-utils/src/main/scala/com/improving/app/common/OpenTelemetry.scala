package com.improving.app.common

import io.opentelemetry.api
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{LongCounter, Meter}
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME


object OpenTelemetry {
  private[common] lazy val sdk: api.OpenTelemetry = GlobalOpenTelemetry.get()
}

case class OpenTelemetry(serviceName: String, prometheusPort: Int = 9090) {
  private val resource: Resource = Resource.getDefault.merge(Resource.builder.put(SERVICE_NAME, serviceName).build)
  private val openTelemetrySdk: OpenTelemetrySdk = OpenTelemetrySdk.builder
    .setTracerProvider(
      SdkTracerProvider.builder
        .setResource(resource)
        .addSpanProcessor(SimpleSpanProcessor.create(JaegerGrpcSpanExporter.builder().build()))
        .build
    ).setMeterProvider(
    SdkMeterProvider.builder
      .setResource(resource)
      .registerMetricReader(PrometheusHttpServer.builder.setPort(prometheusPort).build)
      .build
  ).buildAndRegisterGlobal
  Runtime.getRuntime.addShutdownHook(new Thread(() => {
    openTelemetrySdk.close()
  })
  )
}

case class Tracer(scope: String)  {
  private val tracer: api.trace.Tracer = OpenTelemetry.sdk.getTracer(scope)
  def startSpan(name: String): Span = tracer.spanBuilder(name).startSpan
}

case class Counter(name: String, contextName: String, description: String, uOfM: String) extends LongCounter {
  private val meter: Meter = OpenTelemetry.sdk.meterBuilder(contextName).build()
  private val counter: LongCounter = meter
    .counterBuilder(name)
    .setDescription(description)
    .setUnit(uOfM)
    .build()

  def incr(attributes: Attributes = Attributes.empty(), context: Context = Context.current()): Unit =
    counter.add(1L, attributes, context)
  def decr(attributes: Attributes = Attributes.empty(), context: Context = Context.current()): Unit =
    counter.add(-1L, attributes, context)

  override def add(value: Long): Unit = counter.add(value)

  override def add(value: Long, attributes: Attributes): Unit =
    counter.add(value, attributes)

  override def add(value: Long, attributes: Attributes, context: Context): Unit =
    counter.add(value, attributes, context)
}

case class Histogram(name: String, contextName: String, description: String, unit: String) extends api.metrics.LongHistogram {
  private val meter: api.metrics.Meter = OpenTelemetry.sdk.getMeter(contextName)
  private val histogram: api.metrics.LongHistogram =
    meter.histogramBuilder(name).ofLongs.setDescription(description).setUnit(unit).build()

  override def record(value: Long): Unit = histogram.record(value)

  override def record(value: Long, attributes: Attributes): Unit =
    histogram.record(value, attributes)

  override def record(value: Long, attributes: Attributes, context: Context): Unit =
    histogram.record(value, attributes, context)
}

