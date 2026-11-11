package com.observeit.observedapp.infrastructure

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenTelemetryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun openTelemetry(): OpenTelemetry = GlobalOpenTelemetry.get()
}
