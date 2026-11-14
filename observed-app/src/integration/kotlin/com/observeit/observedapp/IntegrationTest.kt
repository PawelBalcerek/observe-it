package com.observeit.observedapp

import com.observeit.observedapp.test.MetricCapture
import com.observeit.observedapp.test.SpanCapture
import com.observeit.observedapp.test.TestOpenTelemetryConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestOpenTelemetryConfiguration::class)
abstract class IntegrationTest {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var metricCapture: MetricCapture

    @Autowired
    protected lateinit var spanCapture: SpanCapture

    protected val restTestClient: RestTestClient by lazy {
        RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }

    @BeforeEach
    @AfterEach
    fun resetTelemetryCapture() {
        metricCapture.drainAndReset()
        spanCapture.reset()
    }
}
