package com.observeit.e2e

import com.observeit.e2e.config.E2eConfig
import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.openSearch
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class MetricsTelemetryE2eTest {
    @Test
    fun `ss4o metrics index template and ingest pipeline exist and index is green with 1 shard and 0 replicas`() {
        // given
        val templateName = "observe-it-ss4o-metrics"
        val pipelineName = "observe-it-metrics-pipeline"

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(METRICS_INDEX)
            val templateExists = openSearch.indexTemplateExists(templateName)
            val pipelineExists = openSearch.ingestPipelineExists(pipelineName)
            val health = openSearch.clusterHealthStatus(METRICS_INDEX)
            val settings = openSearch.indexSettings(METRICS_INDEX)

            assertSoftly {
                templateExists shouldBe true
                pipelineExists shouldBe true
                health shouldBe "green"
                settings.shards shouldBe 1
                settings.replicas shouldBe 0
            }
        }
    }

    @Test
    fun `normalized metric documents exist for both services without data_stream and with DELTA temporality`() {
        // given
        val services = listOf("observed-app", "traffic-generator")
        val nonDeltaQuery = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf(
                        "bool" to mapOf(
                            "should" to listOf(
                                mapOf(
                                    "bool" to mapOf(
                                        "must" to listOf(
                                            mapOf("term" to mapOf("kind" to "SUM")),
                                            mapOf("term" to mapOf("monotonic" to true)),
                                        ),
                                    ),
                                ),
                                mapOf("term" to mapOf("kind" to "HISTOGRAM")),
                            ),
                            "minimum_should_match" to 1,
                        ),
                    ),
                ),
                "must_not" to listOf(
                    mapOf("term" to mapOf("aggregationTemporality" to "AGGREGATION_TEMPORALITY_DELTA")),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(METRICS_INDEX)
            assertSoftly {
                for (service in services) {
                    val count = openSearch.count(
                        METRICS_INDEX,
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("term" to mapOf("service" to service)),
                                    mapOf("term" to mapOf("version" to E2eConfig.imageTag)),
                                    mapOf("term" to mapOf("env" to "local")),
                                ),
                            ),
                        ),
                    )
                    count shouldBeGreaterThan 0L
                }
                openSearch.count(
                    METRICS_INDEX,
                    mapOf("exists" to mapOf("field" to "attributes.data_stream")),
                ) shouldBe 0L
                openSearch.count(METRICS_INDEX, nonDeltaQuery) shouldBe 0L
            }
        }
    }

    @Test
    fun `all 6 observed-app domain meters are recorded in OpenSearch`() {
        // given
        val timeoutHistogramQuery = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("name" to "order.payment.duration")),
                    mapOf("term" to mapOf("attributes.payment.outcome" to "TIMEOUT")),
                    mapOf("range" to mapOf("max" to mapOf("gte" to 1000))),
                    mapOf(
                        "nested" to mapOf(
                            "path" to "buckets",
                            "query" to mapOf("range" to mapOf("buckets.count" to mapOf("gt" to 0))),
                        ),
                    ),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(METRICS_INDEX)
            assertSoftly {
                openSearch.metricAggregationValue(METRICS_INDEX, "orders.created", "sum") shouldBeGreaterThanOrEqual 7.0
                metricTermsKeys("order.state.transitions", "attributes.order.state") shouldContainAll listOf(
                    "RESERVED",
                    "PAID",
                    "SHIPPED",
                    "FAILED",
                    "CANCELLED",
                )
                metricTermsKeys("order.errors", "attributes.error.type") shouldContainAll listOf(
                    "INVALID_STATE_TRANSITION",
                    "OUT_OF_STOCK",
                    "PAYMENT_DECLINED",
                    "GATEWAY_TIMEOUT",
                )
                metricTermsKeys("order.inventory.reservations", "attributes.inventory.outcome") shouldContainAll listOf(
                    "RESERVED",
                    "LOW_STOCK_WARNING",
                    "OUT_OF_STOCK",
                )
                metricTermsKeys("order.payment.duration", "attributes.payment.outcome") shouldContainAll listOf(
                    "SUCCESS",
                    "DECLINED",
                    "TIMEOUT",
                )
                openSearch.count(METRICS_INDEX, timeoutHistogramQuery) shouldBeGreaterThan 0L
                openSearch.metricAggregationValue(
                    METRICS_INDEX,
                    "order.repository.size",
                    "max",
                ) shouldBeGreaterThanOrEqual 7.0
            }
        }
    }

    @Test
    fun `all 4 traffic-generator meters and auto-instrumented HTTP and JVM meters are recorded`() {
        // given
        val autoMeters = listOf(
            "http.server.request.duration",
            "http.client.request.duration",
            "jvm.thread.count",
            "jvm.cpu.recent_utilization",
        )
        val jvmMemoryQuery = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("name" to "jvm.memory.used")),
                    mapOf("range" to mapOf("value@int" to mapOf("gt" to 0))),
                    mapOf("range" to mapOf("value" to mapOf("gt" to 0))),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(METRICS_INDEX)
            assertSoftly {
                metricTermsKeys("traffic.scenarios.executed", "attributes.checkout.scenario") shouldContainAll
                    E2eSuiteHarness.allScenarios
                metricTermsKeys("traffic.scenario.duration", "attributes.checkout.scenario") shouldContainAll
                    E2eSuiteHarness.allScenarios
                openSearch.metricAggregationValue(METRICS_INDEX, "traffic.target_rate", "max") shouldBeGreaterThan 0.0
                openSearch.metricAggregationValue(
                    METRICS_INDEX,
                    "traffic.workers.active",
                    "max",
                ) shouldBeGreaterThan 0.0

                for (autoMeter in autoMeters) {
                    openSearch.count(METRICS_INDEX, mapOf("term" to mapOf("name" to autoMeter))) shouldBeGreaterThan 0L
                }
                openSearch.count(METRICS_INDEX, jvmMemoryQuery) shouldBeGreaterThan 0L
            }
        }
    }

    private fun metricTermsKeys(metricName: String, fieldName: String): Set<String> = openSearch.termsAggregationKeys(
        index = METRICS_INDEX,
        field = fieldName,
        query = mapOf("term" to mapOf("name" to metricName)),
    )

    private companion object {
        const val METRICS_INDEX = "ss4o_metrics-observe-it-local"
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(30)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            E2eSuiteHarness.happyPathOrderId()
        }
    }
}
