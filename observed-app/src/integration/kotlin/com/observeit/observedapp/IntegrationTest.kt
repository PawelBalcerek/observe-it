package com.observeit.observedapp

import com.observeit.observedapp.test.TestMetricsConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestMetricsConfiguration::class)
abstract class IntegrationTest {
    @LocalServerPort
    protected var port: Int = 0

    protected val restTestClient: RestTestClient by lazy {
        RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }
}
