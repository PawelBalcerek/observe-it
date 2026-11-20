package com.observeit.e2e.fixtures

import com.observeit.e2e.config.E2eConfig
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import java.time.Duration

object E2eSuiteHarness {
    val docker: DockerClientFixture = DockerClientFixture()
    private val http: HttpJsonClient = HttpJsonClient()
    val actuator: ActuatorClientFixture = ActuatorClientFixture(http)
    private val trafficGenerator: TrafficGeneratorClientFixture = TrafficGeneratorClientFixture(http)
    val openSearch: OpenSearchClientFixture = OpenSearchClientFixture(http)
    val dashboards: DashboardsClientFixture = DashboardsClientFixture(http)
    val prometheus: PrometheusClientFixture = PrometheusClientFixture(http)

    val allScenarios: List<String> = listOf(
        "HAPPY_PATH",
        "LOW_STOCK_WARNING",
        "OUT_OF_STOCK",
        "PAYMENT_DECLINED",
        "PAYMENT_GATEWAY_TIMEOUT",
        "BUYER_CANCELLATION",
        "INVALID_STATE_TRANSITION",
    )

    private val stackReady: Unit by lazy {
        awaitStackReadiness()
    }

    private val triggeredHappyPathOrderId: String by lazy {
        ensureStackReady()
        triggerAllScenarios()
    }

    fun ensureStackReady() {
        stackReady
    }

    fun happyPathOrderId(): String = triggeredHappyPathOrderId

    private fun awaitStackReadiness() {
        await.ignoreExceptions() atMost Duration.ofSeconds(120) untilAsserted {
            docker.containerHealthStatus("opensearch") shouldBe "healthy"
        }
        await.ignoreExceptions() atMost Duration.ofSeconds(120) untilAsserted {
            dashboards.overallStatusState() shouldBe "green"
        }
        await.ignoreExceptions() atMost Duration.ofSeconds(60) untilAsserted {
            docker.containerHealthStatus("prometheus") shouldBe "healthy"
        }
        await.ignoreExceptions() atMost Duration.ofSeconds(60) untilAsserted {
            docker.containerStateStatus("otel-collector") shouldBe "running"
        }
        await.ignoreExceptions() atMost Duration.ofSeconds(120) untilAsserted {
            actuator.healthStatus(E2eConfig.OBSERVED_APP_BASE_URL) shouldBe "UP"
        }
        await.ignoreExceptions() atMost Duration.ofSeconds(120) untilAsserted {
            actuator.healthStatus(E2eConfig.TRAFFIC_GENERATOR_BASE_URL) shouldBe "UP"
        }
        await.ignoreExceptions() atMost Duration.ofSeconds(120) untilAsserted {
            assertSoftly {
                docker.containerStateStatus("opensearch-bootstrap") shouldBe "exited"
                docker.containerExitCode("opensearch-bootstrap") shouldBe 0
            }
        }
    }

    private fun triggerAllScenarios(): String {
        var capturedHappyPathOrderId: String? = null
        for (scenario in allScenarios) {
            val response = trafficGenerator.triggerScenario(scenario)
            response.success shouldBe true
            if (scenario == "HAPPY_PATH") {
                capturedHappyPathOrderId = response.orderId
            }
        }
        val orderId = requireNotNull(capturedHappyPathOrderId) {
            "HAPPY_PATH trigger response did not return an orderId"
        }
        orderId.shouldNotBeBlank()
        return orderId
    }
}
