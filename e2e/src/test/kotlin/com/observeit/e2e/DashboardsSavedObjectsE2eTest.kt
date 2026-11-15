package com.observeit.e2e

import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.dashboards
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class DashboardsSavedObjectsE2eTest {
    @Test
    fun `all 31 OpenSearch Dashboards saved objects exist`() {
        // given
        val expectedSavedObjects = listOf(
            "index-pattern:ss4o_logs",
            "index-pattern:ss4o_metrics",
            "index-pattern:ss4o_traces",
            "search:all-order-logs",
            "search:warnings-and-rejections",
            "search:gateway-timeouts-and-errors",
            "search:checkout-scenario-explorer",
            "search:all-metrics",
            "search:domain-order-metrics",
            "search:all-trace-spans",
            "search:checkout-scenario-traces",
            "search:domain-order-spans",
            "search:error-and-timeout-spans",
            "visualization:traffic-scenarios-by-outcome",
            "visualization:traffic-scenario-duration",
            "visualization:order-state-transitions",
            "visualization:order-errors-by-type",
            "visualization:order-payment-latency",
            "visualization:inventory-reservations-by-outcome",
            "visualization:http-server-requests-and-latency",
            "visualization:jvm-heap-memory-by-service",
            "visualization:runtime-gauges-kpi",
            "visualization:trace-spans-kpi",
            "visualization:spans-by-service-and-kind",
            "visualization:scenario-trace-duration",
            "visualization:domain-span-latency-by-operation",
            "visualization:http-server-span-latency-by-route",
            "visualization:error-spans-by-operation",
            "visualization:top-operations-latency-table",
            "dashboard:observe-it-metrics-overview",
            "dashboard:observe-it-traces-overview",
        )

        // when
        val actualSavedObjects = dashboards.findSavedObjects(
            listOf("index-pattern", "search", "visualization", "dashboard"),
        )

        // then
        actualSavedObjects shouldContainAll expectedSavedObjects
    }

    @Test
    fun `all 3 index patterns have @timestamp populated in fields`() {
        // given
        val indexPatterns = listOf("ss4o_logs", "ss4o_metrics", "ss4o_traces")

        // when
        val timestampCounts = indexPatterns.associateWith { patternId ->
            val fieldsRaw = dashboards.getSavedObject("index-pattern", patternId)
                .path("attributes").path("fields").asString("[]")
            val fieldsNode = MAPPER.readTree(fieldsRaw)
            fieldsNode.values().count { it.path("name").asString("") == "@timestamp" }
        }

        // then
        assertSoftly {
            timestampCounts.forEach { (_, count) ->
                count shouldBe 1
            }
        }
    }

    @Test
    fun `all 4 log saved searches include traceId in columns`() {
        // given
        val logSearchIds = listOf(
            "all-order-logs",
            "warnings-and-rejections",
            "gateway-timeouts-and-errors",
            "checkout-scenario-explorer",
        )

        // when
        val hasTraceIdBySearch = logSearchIds.associateWith { searchId ->
            val columns = dashboards.getSavedObject("search", searchId)
                .path("attributes").path("columns").values()
            columns.any { it.asString("") == "traceId" }
        }

        // then
        assertSoftly {
            hasTraceIdBySearch.forEach { (_, hasTraceId) ->
                hasTraceId shouldBe true
            }
        }
    }

    private companion object {
        val MAPPER: JsonMapper = jacksonMapperBuilder().build()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            E2eSuiteHarness.ensureStackReady()
        }
    }
}
