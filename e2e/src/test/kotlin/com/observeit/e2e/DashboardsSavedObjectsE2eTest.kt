package com.observeit.e2e

import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.dashboards
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class DashboardsSavedObjectsE2eTest {
    @Test
    fun `all 34 classic objects, APM datasets, data-connection, and correlations exist in observe-it workspace`() {
        // given
        val expectedSavedObjects = listOf(
            "index-pattern:ss4o_logs",
            "index-pattern:ss4o_metrics",
            "index-pattern:ss4o_traces",
            "index-pattern:otel-v1-apm-span",
            "index-pattern:otel-v2-apm-service-map",
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
            "search:mongodb-client-spans",
            "visualization:traffic-scenarios-by-outcome",
            "visualization:traffic-scenario-duration",
            "visualization:order-state-transitions",
            "visualization:order-errors-by-type",
            "visualization:order-payment-latency",
            "visualization:inventory-reservations-by-outcome",
            "visualization:http-server-requests-and-latency",
            "visualization:jvm-heap-memory-by-service",
            "visualization:runtime-gauges-kpi",
            "visualization:mongodb-operation-duration",
            "visualization:trace-spans-kpi",
            "visualization:spans-by-service-and-kind",
            "visualization:scenario-trace-duration",
            "visualization:domain-span-latency-by-operation",
            "visualization:http-server-span-latency-by-route",
            "visualization:error-spans-by-operation",
            "visualization:top-operations-latency-table",
            "visualization:mongodb-span-latency-by-operation",
            "dashboard:observe-it-metrics-overview",
            "dashboard:observe-it-traces-overview",
            "data-connection:observe-it-prometheus",
            "correlations:trace-to-logs-otel-v1-apm-span",
            "correlations:apm-config-observe-it",
        )

        // when
        val actualSavedObjects = dashboards.findSavedObjects(
            workspaceId = WORKSPACE_ID,
            types = listOf(
                "index-pattern",
                "search",
                "visualization",
                "dashboard",
                "data-connection",
                "correlations",
            ),
        )

        // then
        actualSavedObjects shouldContainAll expectedSavedObjects
    }

    @Test
    fun `all 3 classic index patterns have @timestamp populated in fields`() {
        // given
        val indexPatterns = listOf("ss4o_logs", "ss4o_metrics", "ss4o_traces")

        // when
        val timestampCounts = indexPatterns.associateWith { patternId ->
            val fieldsRaw = dashboards.getSavedObject(WORKSPACE_ID, "index-pattern", patternId)
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
            val columns = dashboards.getSavedObject(WORKSPACE_ID, "search", searchId)
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

    @Test
    fun `dataset index patterns configure signal types, display names, and log schema mappings`() {
        // given
        val workspaceId = WORKSPACE_ID

        // when
        val logsAttributes = dashboards.getSavedObject(workspaceId, "index-pattern", "ss4o_logs")
            .path("attributes")
        val metricsAttributes = dashboards.getSavedObject(workspaceId, "index-pattern", "ss4o_metrics")
            .path("attributes")
        val classicTracesAttributes = dashboards.getSavedObject(workspaceId, "index-pattern", "ss4o_traces")
            .path("attributes")
        val apmSpanAttributes = dashboards.getSavedObject(workspaceId, "index-pattern", "otel-v1-apm-span")
            .path("attributes")
        val serviceMapAttributes = dashboards.getSavedObject(workspaceId, "index-pattern", "otel-v2-apm-service-map")
            .path("attributes")
        val otelLogsMapping = MAPPER.readTree(logsAttributes.path("schemaMappings").asString("{}"))
            .path("otelLogs")

        // then
        assertSoftly {
            logsAttributes.path("displayName").asString("") shouldBe "Observe-It Logs"
            logsAttributes.path("signalType").asString("") shouldBe "logs"
            otelLogsMapping.path("timestamp").asString("") shouldBe "@timestamp"
            otelLogsMapping.path("traceId").asString("") shouldBe "traceId"
            otelLogsMapping.path("spanId").asString("") shouldBe "spanId"
            otelLogsMapping.path("serviceName").asString("") shouldBe "service"

            metricsAttributes.path("signalType").asString("").shouldBeEmpty()
            classicTracesAttributes.path("signalType").asString("").shouldBeEmpty()

            apmSpanAttributes.path("title").asString("") shouldBe "otel-v1-apm-span-*"
            apmSpanAttributes.path("timeFieldName").asString("") shouldBe "endTime"
            apmSpanAttributes.path("displayName").asString("") shouldBe "Observe-It APM Traces"
            apmSpanAttributes.path("signalType").asString("") shouldBe "traces"

            serviceMapAttributes.path("title").asString("") shouldBe "otel-v2-apm-service-map*"
            serviceMapAttributes.path("timeFieldName").asString("") shouldBe "timestamp"
            serviceMapAttributes.path("displayName").asString("") shouldBe "Observe-It Service Map"
        }
    }

    @Test
    fun `Prometheus data-connection and cross-signal correlations reference the expected datasets`() {
        // given
        val workspaceId = WORKSPACE_ID

        // when
        val prometheusConnection = dashboards.getSavedObject(
            workspaceId,
            "data-connection",
            "observe-it-prometheus",
        )
        val traceToLogs = dashboards.getSavedObject(
            workspaceId,
            "correlations",
            "trace-to-logs-otel-v1-apm-span",
        )
        val apmConfig = dashboards.getSavedObject(
            workspaceId,
            "correlations",
            "apm-config-observe-it",
        )

        val traceToLogsRefs = traceToLogs.referenceMap()
        val traceToLogsMeta = traceToLogs.path("attributes").path("entities").values()
            .firstOrNull { it.has("logsDataset") }
            ?.path("logsDataset")
            ?.path("meta")

        val apmConfigRefs = apmConfig.referenceMap()
        val apmWindowDuration = apmConfig.path("attributes").path("entities").values()
            .firstOrNull { it.has("windowDuration") }
            ?.path("windowDuration")
            ?.asInt(-1)

        // then
        assertSoftly {
            prometheusConnection.path("attributes").path("connectionId").asString("") shouldBe "observe_it_prometheus"
            prometheusConnection.path("attributes").path("type").asString("") shouldBe "Prometheus"

            traceToLogs.path("attributes").path("correlationType").asString("") shouldBe
                "trace-to-logs-otel-v1-apm-span-*"
            traceToLogsRefs shouldBe mapOf(
                "entities[0].index" to "index-pattern:otel-v1-apm-span",
                "entities[1].index" to "index-pattern:ss4o_logs",
            )
            traceToLogsMeta?.path("logTraceIdField")?.asString("") shouldBe "traceId"
            traceToLogsMeta?.path("logSpanIdField")?.asString("") shouldBe "spanId"
            traceToLogsMeta?.path("logMessage")?.asString("") shouldBe "message"
            traceToLogsMeta?.path("timestamp")?.asString("") shouldBe "@timestamp"

            apmConfig.path("attributes").path("correlationType").asString("") shouldBe "APM-Config-observe-it"
            apmWindowDuration shouldBe 60
            apmConfigRefs shouldBe mapOf(
                "entities[0].index" to "index-pattern:otel-v1-apm-span",
                "entities[1].index" to "index-pattern:otel-v2-apm-service-map",
                "entities[2].dataConnection" to "data-connection:observe-it-prometheus",
            )
        }
    }

    private fun JsonNode.referenceMap(): Map<String, String> = path("references").values().associate { ref ->
        ref.path("name").asString("") to "${ref.path("type").asString("")}:${ref.path("id").asString("")}"
    }

    private companion object {
        const val WORKSPACE_ID = "observe-it"
        val MAPPER: JsonMapper = jacksonMapperBuilder().build()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            E2eSuiteHarness.ensureStackReady()
        }
    }
}
