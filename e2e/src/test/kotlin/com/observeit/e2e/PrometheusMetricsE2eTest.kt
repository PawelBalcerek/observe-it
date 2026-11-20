package com.observeit.e2e

import com.observeit.e2e.config.E2eConfig
import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.prometheus
import com.observeit.e2e.fixtures.PrometheusClientFixture.VectorSample
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class PrometheusMetricsE2eTest {
    @Test
    fun `prometheus ingests OTLP metrics with promoted resource labels from both services`() {
        // given
        val expectedVersion = E2eConfig.imageTag

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            val ordersCreated = prometheus.queryVector(ORDERS_CREATED_TOTAL)
            val trafficScenarios = prometheus.queryVector("traffic_scenarios_executed_total")
            val httpServerRequests = prometheus.queryVector("http_server_request_duration_seconds_count")
            val dbClientOperations = prometheus.queryVector("db_client_operation_duration_seconds_count")

            assertSoftly {
                ordersCreated.shouldNotBeEmpty()
                ordersCreated.sumOf { it.value } shouldBeGreaterThanOrEqual 7.0
                ordersCreated.forEach { sample ->
                    sample.shouldHavePromotedResourceLabels(setOf(OBSERVED_APP), expectedVersion)
                }

                trafficScenarios.shouldNotBeEmpty()
                trafficScenarios.mapNotNull { it.labels["checkout_scenario"] } shouldContainAll
                    E2eSuiteHarness.allScenarios
                trafficScenarios.forEach { sample ->
                    sample.shouldHavePromotedResourceLabels(setOf(TRAFFIC_GENERATOR), expectedVersion)
                }

                httpServerRequests.shouldNotBeEmpty()
                httpServerRequests.sumOf { it.value } shouldBeGreaterThan 0.0
                httpServerRequests.forEach { sample ->
                    sample.shouldHavePromotedResourceLabels(setOf(OBSERVED_APP, TRAFFIC_GENERATOR), expectedVersion)
                }

                dbClientOperations.shouldNotBeEmpty()
                dbClientOperations.sumOf { it.value } shouldBeGreaterThan 0.0
                dbClientOperations.forEach { sample ->
                    sample.shouldHavePromotedResourceLabels(setOf(OBSERVED_APP), expectedVersion)
                }
            }
        }
    }

    @Test
    fun `prometheus metrics exclude actuator health and MongoDB admin noise`() {
        // given
        val actuatorHealthRouteQuery = """http_server_request_duration_seconds_count{http_route="/actuator/health"}"""
        val actuatorHealthPathQuery = """http_server_request_duration_seconds_count{url_path="/actuator/health"}"""
        val mongoAdminOpsQuery =
            """db_client_operation_duration_seconds_count{db_operation_name=~"hello|isMaster|listDatabases"}"""

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            val ordersCreated = prometheus.queryVector(ORDERS_CREATED_TOTAL)
            val actuatorRouteNoise = prometheus.queryVector(actuatorHealthRouteQuery)
            val actuatorPathNoise = prometheus.queryVector(actuatorHealthPathQuery)
            val mongoAdminNoise = prometheus.queryVector(mongoAdminOpsQuery)

            assertSoftly {
                ordersCreated.shouldNotBeEmpty()
                actuatorRouteNoise.shouldBeEmpty()
                actuatorPathNoise.shouldBeEmpty()
                mongoAdminNoise.shouldBeEmpty()
            }
        }
    }

    private fun VectorSample.shouldHavePromotedResourceLabels(expectedServices: Set<String>, expectedVersion: String) {
        labels["service_name"] shouldBeIn expectedServices
        labels["service_version"] shouldBe expectedVersion
        labels["deployment_environment_name"] shouldBe "local"
    }

    private companion object {
        const val ORDERS_CREATED_TOTAL = "orders_created_total"
        const val OBSERVED_APP = "observed-app"
        const val TRAFFIC_GENERATOR = "traffic-generator"
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(30)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            E2eSuiteHarness.happyPathOrderId()
        }
    }
}
