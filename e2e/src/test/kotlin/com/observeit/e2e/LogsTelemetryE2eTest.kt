package com.observeit.e2e

import com.observeit.e2e.config.E2eConfig
import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.openSearch
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class LogsTelemetryE2eTest {
    @Test
    fun `ss4o logs index template exists and index is green with 1 shard and 0 replicas`() {
        // given
        val templateName = "observe-it-ss4o-logs"

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            val templateExists = openSearch.indexTemplateExists(templateName)
            val health = openSearch.clusterHealthStatus(LOGS_INDEX)
            val settings = openSearch.indexSettings(LOGS_INDEX)

            assertSoftly {
                templateExists shouldBe true
                health shouldBe "green"
                settings.shards shouldBe 1
                settings.replicas shouldBe 0
            }
        }
    }

    @Test
    fun `log documents exist for both services and cover all 7 checkout scenarios`() {
        // given
        val expectedScenarios = E2eSuiteHarness.allScenarios

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            val observedAppCount = openSearch.count(LOGS_INDEX, mapOf("term" to mapOf("service" to "observed-app")))
            val trafficGenCount = openSearch.count(LOGS_INDEX, mapOf("term" to mapOf("service" to "traffic-generator")))
            val scenarioKeys = openSearch.termsAggregationKeys(LOGS_INDEX, "attributes.checkout.scenario")

            assertSoftly {
                observedAppCount shouldBeGreaterThan 0L
                trafficGenCount shouldBeGreaterThan 0L
                scenarioKeys shouldContainAll expectedScenarios
            }
        }
    }

    @Test
    fun `correlated log documents contain order id, customer id, thread name, code namespace, and W3C IDs`() {
        // given
        val query = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("exists" to mapOf("field" to "attributes.order.id")),
                    mapOf("exists" to mapOf("field" to "attributes.customer.id")),
                    mapOf("exists" to mapOf("field" to "traceId")),
                    mapOf("exists" to mapOf("field" to "spanId")),
                    mapOf("exists" to mapOf("field" to "attributes.thread.name")),
                    mapOf("exists" to mapOf("field" to "attributes.code.namespace")),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            val response = openSearch.search(LOGS_INDEX, query = query)
            val totalHits = response.path("hits").path("total").path("value").asLong(0L)
            val hits = response.path("hits").path("hits").values()

            assertSoftly {
                totalHits shouldBeGreaterThan 0L
                hits.any { hit ->
                    val source = hit.path("_source")
                    TRACE_ID_REGEX.matches(source.path("traceId").asString("")) &&
                        SPAN_ID_REGEX.matches(source.path("spanId").asString(""))
                } shouldBe true
            }
        }
    }

    @Test
    fun `WARN logs exist for domain warning and rejection scenarios without exception stacktraces`() {
        // given
        val warnScenarios = WARN_SCENARIOS

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            assertSoftly {
                for (scenario in warnScenarios) {
                    val withoutStacktrace = openSearch.count(
                        LOGS_INDEX,
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("term" to mapOf("level" to "WARN")),
                                    mapOf("term" to mapOf("attributes.checkout.scenario" to scenario)),
                                ),
                                "must_not" to listOf(mapOf("exists" to mapOf("field" to "exception"))),
                            ),
                        ),
                    )
                    val withStacktrace = openSearch.count(
                        LOGS_INDEX,
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("term" to mapOf("level" to "WARN")),
                                    mapOf("term" to mapOf("attributes.checkout.scenario" to scenario)),
                                    mapOf("exists" to mapOf("field" to "exception")),
                                ),
                            ),
                        ),
                    )
                    withoutStacktrace shouldBeGreaterThan 0L
                    withStacktrace shouldBe 0L
                }
            }
        }
    }

    @Test
    fun `ERROR logs exist for PAYMENT_GATEWAY_TIMEOUT with error type, latency 1000ms+, and stacktrace`() {
        // given
        val query = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("level" to "ERROR")),
                    mapOf("term" to mapOf("attributes.checkout.scenario" to "PAYMENT_GATEWAY_TIMEOUT")),
                    mapOf("term" to mapOf("attributes.error.type" to "GATEWAY_TIMEOUT")),
                    mapOf("range" to mapOf("attributes.payment.latency_ms" to mapOf("gte" to 1000))),
                    mapOf("exists" to mapOf("field" to "exception")),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            val response = openSearch.search(LOGS_INDEX, query = query)
            val totalHits = response.path("hits").path("total").path("value").asLong(0L)
            val firstException = response.path("hits").path("hits").values().firstOrNull()
                ?.path("_source")?.path("exception")?.asString("")

            assertSoftly {
                totalHits shouldBeGreaterThan 0L
                firstException.shouldNotBeBlank()
            }
        }
    }

    @Test
    fun `zero unexpected WARN, ERROR, or FATAL logs exist`() {
        // given
        val allowedWarnScenarios = WARN_SCENARIOS
        val allowedErrorScenarios = listOf("PAYMENT_GATEWAY_TIMEOUT")

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            val unexpectedWarns = countUnexpectedLogs("WARN", allowedWarnScenarios)
            val unexpectedErrors = countUnexpectedLogs("ERROR", allowedErrorScenarios)
            val fatalCount = openSearch.count(LOGS_INDEX, mapOf("term" to mapOf("level" to "FATAL")))

            assertSoftly {
                unexpectedWarns shouldBe 0L
                unexpectedErrors shouldBe 0L
                fatalCount shouldBe 0L
            }
        }
    }

    @Test
    fun `normalized top-level log fields are searchable via ingest pipeline`() {
        // given
        val query = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("level" to "WARN")),
                    mapOf("term" to mapOf("env" to "local")),
                    mapOf("term" to mapOf("service" to "observed-app")),
                    mapOf("term" to mapOf("version" to E2eConfig.imageTag)),
                    mapOf("wildcard" to mapOf("logger" to "com.observeit.*")),
                    mapOf("match_phrase" to mapOf("message" to "Payment declined")),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(LOGS_INDEX)
            val matches = openSearch.count(LOGS_INDEX, query)
            matches shouldBeGreaterThan 0L
        }
    }

    private fun countUnexpectedLogs(severity: String, allowedScenarios: List<String>): Long = openSearch.count(
        LOGS_INDEX,
        mapOf(
            "bool" to mapOf(
                "must" to listOf(mapOf("term" to mapOf("level" to severity))),
                "must_not" to listOf(
                    mapOf("terms" to mapOf("attributes.checkout.scenario" to allowedScenarios)),
                ),
            ),
        ),
    )

    private companion object {
        const val LOGS_INDEX = "ss4o_logs-observe-it-local"
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(30)
        val TRACE_ID_REGEX = Regex("^[0-9a-f]{32}$")
        val SPAN_ID_REGEX = Regex("^[0-9a-f]{16}$")
        val WARN_SCENARIOS = listOf(
            "LOW_STOCK_WARNING",
            "OUT_OF_STOCK",
            "PAYMENT_DECLINED",
            "INVALID_STATE_TRANSITION",
        )

        @JvmStatic
        @BeforeAll
        fun setUp() {
            E2eSuiteHarness.happyPathOrderId()
        }
    }
}
