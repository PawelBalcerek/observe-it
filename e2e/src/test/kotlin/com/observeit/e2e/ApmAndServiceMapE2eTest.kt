package com.observeit.e2e

import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.openSearch
import com.observeit.e2e.fixtures.E2eSuiteHarness.prometheus
import com.observeit.e2e.fixtures.PrometheusClientFixture.VectorSample
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.string.shouldNotBeBlank
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class ApmAndServiceMapE2eTest {
    @Test
    fun `APM span index has spans for both services, joins HAPPY_PATH traceId with logs, and records timeout errors`() {
        // given
        val orderId = happyPathOrderId

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(APM_SPAN_INDEX)
            openSearch.refresh(LOGS_INDEX)
            val health = openSearch.clusterHealthStatus(APM_SPAN_INDEX)
            val observedAppCount = openSearch.count(
                APM_SPAN_INDEX,
                mapOf("term" to mapOf("serviceName" to OBSERVED_APP)),
            )
            val trafficGenCount = openSearch.count(
                APM_SPAN_INDEX,
                mapOf("term" to mapOf("serviceName" to TRAFFIC_GENERATOR)),
            )
            val happyPathTraceId = findTraceIdInLogs(
                mapOf(
                    "bool" to mapOf(
                        "must" to listOf(
                            mapOf("term" to mapOf("attributes.checkout.scenario" to "HAPPY_PATH")),
                            mapOf("term" to mapOf("attributes.order.id" to orderId)),
                            mapOf("exists" to mapOf("field" to "traceId")),
                        ),
                    ),
                ),
            )
            val joinedSpanServices = openSearch.termsAggregationKeys(
                APM_SPAN_INDEX,
                field = "serviceName",
                query = mapOf("term" to mapOf("traceId" to happyPathTraceId)),
            )
            val timeoutErrorSpans = openSearch.count(
                APM_SPAN_INDEX,
                mapOf(
                    "bool" to mapOf(
                        "must" to listOf(
                            mapOf("term" to mapOf("serviceName" to OBSERVED_APP)),
                            mapOf("term" to mapOf("attributes.checkout.scenario" to "PAYMENT_GATEWAY_TIMEOUT")),
                            mapOf("term" to mapOf("attributes.error.type" to "GATEWAY_TIMEOUT")),
                            mapOf("term" to mapOf("status.code" to OTEL_ERROR_STATUS_CODE)),
                        ),
                    ),
                ),
            )

            assertSoftly {
                health shouldBeIn ACCEPTABLE_APM_INDEX_HEALTH
                observedAppCount shouldBeGreaterThan 0L
                trafficGenCount shouldBeGreaterThan 0L
                happyPathTraceId.shouldNotBeBlank()
                joinedSpanServices shouldContainAll listOf(OBSERVED_APP, TRAFFIC_GENERATOR)
                timeoutErrorSpans shouldBeGreaterThan 0L
            }
        }
    }

    @Test
    fun `APM service map index captures traffic-generator to observed-app edge and mongodb dependency node`() {
        // given
        val serviceEdgeQuery = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("sourceNode.keyAttributes.name" to TRAFFIC_GENERATOR)),
                    mapOf("term" to mapOf("targetNode.keyAttributes.name" to OBSERVED_APP)),
                ),
            ),
        )
        val mongodbDependencyQuery = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("sourceNode.keyAttributes.name" to OBSERVED_APP)),
                    mapOf("term" to mapOf("targetNode.type" to "database")),
                    mapOf("term" to mapOf("targetNode.keyAttributes.name" to "mongodb:mongodb")),
                    mapOf("term" to mapOf("targetNode.dependencyAttributes.db.system.name.keyword" to "mongodb")),
                ),
            ),
        )

        // when / then
        await atMost APM_AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(APM_SERVICE_MAP_INDEX)
            val health = openSearch.clusterHealthStatus(APM_SERVICE_MAP_INDEX)
            val serviceEdgeCount = openSearch.count(APM_SERVICE_MAP_INDEX, serviceEdgeQuery)
            val mongodbDependencyCount = openSearch.count(APM_SERVICE_MAP_INDEX, mongodbDependencyQuery)

            assertSoftly {
                health shouldBeIn ACCEPTABLE_APM_INDEX_HEALTH
                serviceEdgeCount shouldBeGreaterThan 0L
                mongodbDependencyCount shouldBeGreaterThan 0L
            }
        }
    }

    @Test
    fun `prometheus ingests span_derived RED metrics for both services with fault count and zero randomKey noise`() {
        // given
        val expectedServices = listOf(OBSERVED_APP, TRAFFIC_GENERATOR)

        // when / then
        await atMost APM_AWAIT_TIMEOUT untilAsserted {
            val requests = prometheus.queryVector("""request{namespace="span_derived"}""")
            val errors = prometheus.queryVector("""error{namespace="span_derived"}""")
            val faults = prometheus.queryVector("""fault{namespace="span_derived"}""")
            val faultsOverWindow = prometheus.queryVector("""sum_over_time(fault{namespace="span_derived"}[15m])""")
            val latencyBuckets = prometheus.queryVector("""latency_seconds_bucket{namespace="span_derived"}""")
            val randomKeySeries = prometheus.queryVector("""{namespace="span_derived", randomKey!=""}""")

            assertSoftly {
                requests.shouldCoverServices(expectedServices)
                errors.shouldCoverServices(expectedServices)
                faults.shouldCoverServices(expectedServices)
                faultsOverWindow.sumOf { it.value } shouldBeGreaterThan 0.0
                latencyBuckets.shouldCoverServices(expectedServices)
                randomKeySeries.shouldBeEmpty()
            }
        }
    }

    private fun List<VectorSample>.shouldCoverServices(expectedServices: List<String>) {
        shouldNotBeEmpty()
        mapNotNull { it.labels["service"] }.toSet() shouldContainAll expectedServices
    }

    private fun findTraceIdInLogs(query: Map<String, Any>): String {
        val response = openSearch.search(LOGS_INDEX, query = query, size = 1)
        return response.path("hits").path("hits").values().firstOrNull()
            ?.path("_source")?.path("traceId")?.asString("")
            .orEmpty()
    }

    private companion object {
        const val APM_SPAN_INDEX = "otel-v1-apm-span-*"
        const val APM_SERVICE_MAP_INDEX = "otel-v2-apm-service-map*"
        const val LOGS_INDEX = "ss4o_logs-observe-it-local"
        const val OBSERVED_APP = "observed-app"
        const val TRAFFIC_GENERATOR = "traffic-generator"
        const val OTEL_ERROR_STATUS_CODE = 2
        val ACCEPTABLE_APM_INDEX_HEALTH = setOf("green", "yellow")
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(30)
        val APM_AWAIT_TIMEOUT: Duration = Duration.ofSeconds(90)

        lateinit var happyPathOrderId: String

        @JvmStatic
        @BeforeAll
        fun setUp() {
            happyPathOrderId = E2eSuiteHarness.happyPathOrderId()
        }
    }
}
