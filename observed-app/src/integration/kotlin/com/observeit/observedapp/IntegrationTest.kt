package com.observeit.observedapp

import com.observeit.observedapp.order.infrastructure.OrderDocument
import com.observeit.observedapp.test.MetricCapture
import com.observeit.observedapp.test.SpanCapture
import com.observeit.observedapp.test.TestMongoConfiguration
import com.observeit.observedapp.test.TestOpenTelemetryConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestOpenTelemetryConfiguration::class, TestMongoConfiguration::class)
abstract class IntegrationTest {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var metricCapture: MetricCapture

    @Autowired
    protected lateinit var spanCapture: SpanCapture

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    protected val restTestClient: RestTestClient by lazy {
        RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }

    @BeforeEach
    @AfterEach
    fun resetState() {
        metricCapture.drainAndReset()
        spanCapture.reset()
        mongoTemplate.remove(Query(), OrderDocument::class.java)
    }
}
