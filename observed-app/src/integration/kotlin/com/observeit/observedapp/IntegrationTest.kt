package com.observeit.observedapp

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class IntegrationTest {
    @LocalServerPort
    protected var port: Int = 0

    protected val restTestClient: RestTestClient by lazy {
        RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }
}
