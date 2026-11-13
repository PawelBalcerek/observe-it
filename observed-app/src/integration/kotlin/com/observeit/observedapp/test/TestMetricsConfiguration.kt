package com.observeit.observedapp.test

import io.opentelemetry.api.OpenTelemetry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestMetricsConfiguration {

    @Bean
    fun metricCapture(): MetricCapture = MetricCapture()

    @Bean
    @Primary
    fun openTelemetry(metricCapture: MetricCapture): OpenTelemetry = metricCapture.openTelemetry
}
