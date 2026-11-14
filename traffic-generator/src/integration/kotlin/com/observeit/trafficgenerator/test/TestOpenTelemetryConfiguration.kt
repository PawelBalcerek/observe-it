package com.observeit.trafficgenerator.test

import io.opentelemetry.sdk.OpenTelemetrySdk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestOpenTelemetryConfiguration {

    @Bean
    fun metricCapture(): MetricCapture = MetricCapture()

    @Bean
    fun spanCapture(): SpanCapture = SpanCapture()

    @Bean(destroyMethod = "close")
    @Primary
    fun openTelemetry(metricCapture: MetricCapture, spanCapture: SpanCapture): OpenTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setMeterProvider(metricCapture.meterProvider)
            .setTracerProvider(spanCapture.tracerProvider)
            .setPropagators(spanCapture.propagators)
            .build()
}
