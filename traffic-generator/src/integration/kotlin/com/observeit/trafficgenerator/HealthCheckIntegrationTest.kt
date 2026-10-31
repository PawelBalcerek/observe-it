package com.observeit.trafficgenerator

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class HealthCheckIntegrationTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `check returns true when health endpoint responds 200 with UP status`() {
        // given
        server.createContext("/actuator/health") { exchange ->
            val responseBody = """{"status":"UP"}"""
            exchange.sendResponseHeaders(200, responseBody.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(responseBody.toByteArray()) }
        }
        server.start()

        // when
        val isHealthy = HealthCheck.check("http://localhost:$port/actuator/health")

        // then
        isHealthy shouldBe true
    }

    @Test
    fun `check returns false when health endpoint responds 503 DOWN`() {
        // given
        server.createContext("/actuator/health") { exchange ->
            val responseBody = """{"status":"DOWN"}"""
            exchange.sendResponseHeaders(503, responseBody.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(responseBody.toByteArray()) }
        }
        server.start()

        // when
        val isHealthy = HealthCheck.check("http://localhost:$port/actuator/health")

        // then
        isHealthy shouldBe false
    }

    @Test
    fun `check returns false when connection is refused`() {
        // given
        val unusedPort = port
        server.stop(0)

        // when
        val isHealthy = HealthCheck.check("http://localhost:$unusedPort/actuator/health")

        // then
        isHealthy shouldBe false
    }
}
