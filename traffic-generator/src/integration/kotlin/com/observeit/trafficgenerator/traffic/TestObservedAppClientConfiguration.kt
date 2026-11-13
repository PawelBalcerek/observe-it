package com.observeit.trafficgenerator.traffic

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

@TestConfiguration
class TestObservedAppClientConfiguration {
    private val builder = RestClient.builder().baseUrl("http://localhost:8080")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.build()

    @Bean
    fun mockServer(): MockRestServiceServer = server

    @Bean
    @Primary
    fun testObservedAppRestClient(): RestClient = client
}
