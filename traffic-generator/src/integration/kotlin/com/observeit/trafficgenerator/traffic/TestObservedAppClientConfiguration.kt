package com.observeit.trafficgenerator.traffic

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

@TestConfiguration
class TestObservedAppClientConfiguration(openTelemetry: OpenTelemetry) {
    private val builder = RestClient.builder()
        .baseUrl("http://localhost:8080")
        .requestInterceptor { request, body, execution ->
            openTelemetry.propagators.textMapPropagator.inject(
                Context.current(),
                request.headers,
            ) { headers, key, value -> headers?.set(key, value) }
            execution.execute(request, body)
        }
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.build()

    @Bean
    fun mockServer(): MockRestServiceServer = server

    @Bean
    @Primary
    fun testObservedAppRestClient(): RestClient = client
}
