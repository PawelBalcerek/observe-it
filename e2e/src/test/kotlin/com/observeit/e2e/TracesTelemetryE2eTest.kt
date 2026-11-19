package com.observeit.e2e

import com.observeit.e2e.config.E2eConfig
import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.openSearch
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class TracesTelemetryE2eTest {
    @Test
    fun `ss4o traces index template and ingest pipeline exist and index is green with 1 shard and 0 replicas`() {
        // given
        val templateName = "observe-it-ss4o-traces"
        val pipelineName = "observe-it-traces-pipeline"

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            val templateExists = openSearch.indexTemplateExists(templateName)
            val pipelineExists = openSearch.ingestPipelineExists(pipelineName)
            val health = openSearch.clusterHealthStatus(TRACES_INDEX)
            val settings = openSearch.indexSettings(TRACES_INDEX)

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
    fun `normalized trace documents have valid timestamps, non-negative durations, and zero attributes data_stream`() {
        // given
        val services = listOf("observed-app", "traffic-generator")
        val invalidTimestampQuery = mapOf(
            "bool" to mapOf(
                "should" to listOf(
                    mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "@timestamp"))))),
                    mapOf("range" to mapOf("@timestamp" to mapOf("lt" to "2020-01-01T00:00:00Z"))),
                ),
                "minimum_should_match" to 1,
            ),
        )
        val invalidDurationQuery = mapOf(
            "bool" to mapOf(
                "should" to listOf(
                    mapOf(
                        "bool" to mapOf(
                            "must_not" to listOf(mapOf("exists" to mapOf("field" to "durationInNanos"))),
                        ),
                    ),
                    mapOf(
                        "bool" to mapOf(
                            "must_not" to listOf(mapOf("exists" to mapOf("field" to "durationMs"))),
                        ),
                    ),
                    mapOf("range" to mapOf("durationInNanos" to mapOf("lt" to 0))),
                    mapOf("range" to mapOf("durationMs" to mapOf("lt" to 0))),
                ),
                "minimum_should_match" to 1,
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            assertSoftly {
                for (service in services) {
                    val count = openSearch.count(
                        TRACES_INDEX,
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("term" to mapOf("service" to service)),
                                    mapOf("term" to mapOf("serviceName" to service)),
                                    mapOf("term" to mapOf("version" to E2eConfig.imageTag)),
                                    mapOf("term" to mapOf("env" to "local")),
                                ),
                            ),
                        ),
                    )
                    count shouldBeGreaterThan 0L
                }
                openSearch.count(TRACES_INDEX, invalidTimestampQuery) shouldBe 0L
                openSearch.count(
                    TRACES_INDEX,
                    mapOf("exists" to mapOf("field" to "attributes.data_stream")),
                ) shouldBe 0L
                openSearch.count(TRACES_INDEX, invalidDurationQuery) shouldBe 0L
            }
        }
    }

    @Test
    fun `all 7 scenarios, 8 custom domain spans, Client and Server kinds, and span events are recorded`() {
        // given
        val expectedSpanNames = listOf(
            "checkout.scenario",
            "order.create",
            "order.get",
            "order.reserve",
            "order.pay",
            "payment.process",
            "order.ship",
            "order.cancel",
        )
        val expectedKinds = listOf("Client", "Server")
        val expectedEvents = listOf("inventory.reservation", "order.state.transitioned")

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            val scenarioKeys = openSearch.termsAggregationKeys(TRACES_INDEX, "attributes.checkout.scenario")

            assertSoftly {
                scenarioKeys shouldContainAll E2eSuiteHarness.allScenarios
                for (spanName in expectedSpanNames) {
                    openSearch.count(TRACES_INDEX, mapOf("term" to mapOf("name" to spanName))) shouldBeGreaterThan 0L
                }
                for (kind in expectedKinds) {
                    openSearch.count(TRACES_INDEX, mapOf("term" to mapOf("kind" to kind))) shouldBeGreaterThan 0L
                }
                for (eventName in expectedEvents) {
                    openSearch.count(
                        TRACES_INDEX,
                        mapOf("term" to mapOf("events.name" to eventName)),
                    ) shouldBeGreaterThan 0L
                }
            }
        }
    }

    @Test
    fun `HAPPY_PATH order produces a complete distributed parent-child span hierarchy across services`() {
        // given
        val orderId = happyPathOrderId

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            val traceId = findHappyPathTraceId(orderId)
            val spansResponse = openSearch.search(
                TRACES_INDEX,
                query = mapOf("term" to mapOf("traceId" to traceId)),
                size = 100,
            )
            val spans = spansResponse.path("hits").path("hits").values().map { it.path("_source") }
            val services = spans.map { it.path("service").asString("") }.toSet()
            val allSpanIds = spans.map { it.path("spanId").asString("") }.toSet()
            val rootSpans = spans.filter { it.path("parentSpanId").asString("").isEmpty() }
            val childParentIds = spans
                .map { it.path("parentSpanId").asString("") }
                .filter { it.isNotEmpty() }

            assertSoftly {
                services shouldContainAll listOf("traffic-generator", "observed-app")
                rootSpans.size shouldBe 1
                childParentIds.all { it in allSpanIds } shouldBe true
                spans.any {
                    it.path("service").asString("") == "traffic-generator" &&
                        it.path("kind").asString("") in CLIENT_KINDS
                } shouldBe true
                spans.any {
                    it.path("service").asString("") == "observed-app" &&
                        it.path("kind").asString("") in SERVER_KINDS
                } shouldBe true
                for (spanName in HAPPY_PATH_LIFECYCLE_SPANS) {
                    spans.count { it.path("name").asString("") == spanName } shouldBe 1
                }
            }
        }
    }

    @Test
    fun `span status discipline distinguishes Unset on 4xx rejections from Error with exception on gateway timeout`() {
        // given
        val rejectionErrors = listOf("OUT_OF_STOCK", "PAYMENT_DECLINED", "INVALID_STATE_TRANSITION")
        val timeoutQuery = mapOf(
            "bool" to mapOf(
                "must" to listOf(
                    mapOf("term" to mapOf("service" to "observed-app")),
                    mapOf("term" to mapOf("attributes.error.type" to "GATEWAY_TIMEOUT")),
                    mapOf("terms" to mapOf("status.code" to listOf("ERROR", "Error", "STATUS_CODE_ERROR"))),
                    mapOf("term" to mapOf("events.name" to "exception")),
                    mapOf("exists" to mapOf("field" to "events.attributes.exception.stacktrace")),
                ),
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            assertSoftly {
                for (errorType in rejectionErrors) {
                    val total = openSearch.count(
                        TRACES_INDEX,
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("term" to mapOf("service" to "observed-app")),
                                    mapOf("term" to mapOf("attributes.error.type" to errorType)),
                                ),
                            ),
                        ),
                    )
                    val invalidRejectionSpans = openSearch.count(
                        TRACES_INDEX,
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("term" to mapOf("service" to "observed-app")),
                                    mapOf("term" to mapOf("attributes.error.type" to errorType)),
                                ),
                                "should" to listOf(
                                    mapOf(
                                        "bool" to mapOf(
                                            "must_not" to listOf(
                                                mapOf(
                                                    "terms" to mapOf(
                                                        "status.code" to listOf("UNSET", "Unset", "STATUS_CODE_UNSET"),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    mapOf("term" to mapOf("events.name" to "exception")),
                                ),
                                "minimum_should_match" to 1,
                            ),
                        ),
                    )
                    total shouldBeGreaterThan 0L
                    invalidRejectionSpans shouldBe 0L
                }
                openSearch.count(TRACES_INDEX, timeoutQuery) shouldBeGreaterThan 0L
            }
        }
    }

    @Test
    fun `logs and traces correlate on HAPPY_PATH traceId with exact spanId matches`() {
        // given
        val orderId = happyPathOrderId

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            openSearch.refresh(LOGS_INDEX)
            val traceId = findHappyPathTraceId(orderId)
            val traceSpanIds = openSearch.search(
                TRACES_INDEX,
                query = mapOf("term" to mapOf("traceId" to traceId)),
                size = 100,
            ).path("hits").path("hits").values().map { it.path("_source").path("spanId").asString("") }.toSet()

            val logsResponse = openSearch.search(
                LOGS_INDEX,
                query = mapOf("term" to mapOf("traceId" to traceId)),
                size = 50,
            )
            val logsCount = logsResponse.path("hits").path("total").path("value").asLong(0L)
            val matchedSpanIds = logsResponse.path("hits").path("hits").values()
                .map { it.path("_source").path("spanId").asString("") }
                .count { it.isNotEmpty() && it in traceSpanIds }

            assertSoftly {
                logsCount shouldBeGreaterThan 0L
                matchedSpanIds.toLong() shouldBeGreaterThan 0L
            }
        }
    }

    @Test
    fun `MongoDB Client spans use stable database semconv, filter noise, and parent to HAPPY_PATH order spans`() {
        // given
        val orderId = happyPathOrderId
        val noiseQuery = mapOf(
            "bool" to mapOf(
                "should" to listOf(
                    mapOf("term" to mapOf("name" to "count orders")),
                    mapOf(
                        "terms" to mapOf(
                            "attributes.db.operation.name" to listOf("hello", "isMaster", "listDatabases"),
                        ),
                    ),
                    mapOf("term" to mapOf("attributes.http.route" to "/actuator/health")),
                    mapOf("term" to mapOf("attributes.url.path" to "/actuator/health")),
                ),
                "minimum_should_match" to 1,
            ),
        )

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            openSearch.refresh(TRACES_INDEX)
            val traceId = findHappyPathTraceId(orderId)
            val traceSpans = openSearch.search(
                TRACES_INDEX,
                query = mapOf("term" to mapOf("traceId" to traceId)),
                size = 100,
            ).path("hits").path("hits").values().map { it.path("_source") }

            val spanIdsByName = traceSpans.associate {
                it.path("name").asString("") to it.path("spanId").asString("")
            }
            val mongoSpans = traceSpans.filter {
                it.path("attributes").path("db.system.name").asString("") == "mongodb"
            }
            val findSpans = mongoSpans.filter { it.path("name").asString("") == "find orders" }
            val updateSpans = mongoSpans.filter { it.path("name").asString("") == "update orders" }

            assertSoftly {
                findSpans.size shouldBe 4
                updateSpans.size shouldBe 4
                (findSpans + updateSpans).forEach { span ->
                    span.path("kind").asString("") shouldBeIn CLIENT_KINDS
                    val attributes = span.path("attributes")
                    attributes.path("db.namespace").asString("") shouldBe "observe_it"
                    attributes.path("db.collection.name").asString("") shouldBe "orders"
                    val queryText = attributes.path("db.query.text").asString("")
                    queryText.shouldNotBeBlank()
                    queryText shouldContain "\"?\""
                    queryText shouldNotContain orderId
                }
                findSpans.map { it.path("parentSpanId").asString("") }.toSet() shouldBe setOf(
                    spanIdsByName.getValue("order.reserve"),
                    spanIdsByName.getValue("order.pay"),
                    spanIdsByName.getValue("order.ship"),
                    spanIdsByName.getValue("order.get"),
                )
                updateSpans.map { it.path("parentSpanId").asString("") }.toSet() shouldBe setOf(
                    spanIdsByName.getValue("order.create"),
                    spanIdsByName.getValue("order.reserve"),
                    spanIdsByName.getValue("order.pay"),
                    spanIdsByName.getValue("order.ship"),
                )
                openSearch.count(TRACES_INDEX, noiseQuery) shouldBe 0L
            }
        }
    }

    private fun findHappyPathTraceId(orderId: String): String {
        val response = openSearch.search(
            TRACES_INDEX,
            query = mapOf(
                "bool" to mapOf(
                    "must" to listOf(
                        mapOf("term" to mapOf("name" to "checkout.scenario")),
                        mapOf("term" to mapOf("attributes.checkout.scenario" to "HAPPY_PATH")),
                        mapOf("term" to mapOf("attributes.order.id" to orderId)),
                    ),
                ),
            ),
            size = 5,
        )
        response.path("hits").path("total").path("value").asLong(0L) shouldBe 1L
        return response.path("hits").path("hits").first().path("_source").path("traceId").asString("")
    }

    private companion object {
        const val TRACES_INDEX = "ss4o_traces-observe-it-local"
        const val LOGS_INDEX = "ss4o_logs-observe-it-local"
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(30)
        val CLIENT_KINDS = setOf("Client", "CLIENT", "SPAN_KIND_CLIENT")
        val SERVER_KINDS = setOf("Server", "SERVER", "SPAN_KIND_SERVER")
        val HAPPY_PATH_LIFECYCLE_SPANS = listOf(
            "checkout.scenario",
            "order.create",
            "order.reserve",
            "order.pay",
            "payment.process",
            "order.ship",
        )

        lateinit var happyPathOrderId: String

        @JvmStatic
        @BeforeAll
        fun setUp() {
            happyPathOrderId = E2eSuiteHarness.happyPathOrderId()
        }
    }
}
