package com.observeit.e2e.fixtures

import com.observeit.e2e.config.E2eConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

class PrometheusClientFixture(
    private val http: HttpJsonClient,
    private val baseUrl: String = E2eConfig.PROMETHEUS_BASE_URL,
) {
    fun queryVector(promQl: String): List<VectorSample> {
        val encodedQuery = URLEncoder.encode(promQl, UTF_8)
        val response = http.getJson("$baseUrl/api/v1/query?query=$encodedQuery")
        check(response.path("status").asString("") == "success") {
            "Prometheus query '$promQl' did not succeed: $response"
        }
        return response.path("data").path("result").values().map { entry ->
            val labels = entry.path("metric").properties().asSequence()
                .associate { (key, node) -> key to node.asString("") }
            val value = entry.path("value").path(1).asString("").toDoubleOrNull()
                ?: error("Missing numeric sample value for Prometheus query '$promQl': $entry")
            VectorSample(labels = labels, value = value)
        }
    }

    data class VectorSample(val labels: Map<String, String>, val value: Double)
}
