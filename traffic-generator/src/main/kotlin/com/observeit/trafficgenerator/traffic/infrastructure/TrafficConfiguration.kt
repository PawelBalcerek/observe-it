package com.observeit.trafficgenerator.traffic.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@ConfigurationProperties(prefix = "traffic")
data class TrafficProperties(
    val observedAppUrl: String = "http://localhost:8080",
    val ordersPerSecond: Double = DEFAULT_ORDERS_PER_SECOND,
    val workers: WorkersProperties = WorkersProperties(),
) {
    data class WorkersProperties(
        val enabled: Boolean = true,
        val concurrency: Int = DEFAULT_CONCURRENCY,
        val minJitterMs: Long = DEFAULT_MIN_JITTER_MS,
        val maxJitterMs: Long = DEFAULT_MAX_JITTER_MS,
    ) {
        private companion object {
            const val DEFAULT_CONCURRENCY = 3
            const val DEFAULT_MIN_JITTER_MS = 100L
            const val DEFAULT_MAX_JITTER_MS = 500L
        }
    }

    private companion object {
        const val DEFAULT_ORDERS_PER_SECOND = 1.0
    }
}

@Configuration
@EnableConfigurationProperties(TrafficProperties::class)
class TrafficConfiguration {

    @Bean
    fun observedAppRestClient(properties: TrafficProperties): RestClient = RestClient.builder()
        .baseUrl(properties.observedAppUrl)
        .build()
}
