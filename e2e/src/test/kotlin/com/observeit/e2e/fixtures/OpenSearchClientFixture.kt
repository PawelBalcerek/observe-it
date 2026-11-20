package com.observeit.e2e.fixtures

import com.observeit.e2e.config.E2eConfig
import tools.jackson.databind.JsonNode

class OpenSearchClientFixture(
    private val http: HttpJsonClient,
    private val baseUrl: String = E2eConfig.OPENSEARCH_BASE_URL,
) {
    fun indexTemplateExists(templateName: String): Boolean {
        val response = http.getJsonOrNull("$baseUrl/_index_template/$templateName") ?: return false
        return response.path("index_templates").size() > 0
    }

    fun ingestPipelineExists(pipelineName: String): Boolean {
        val response = http.getJsonOrNull("$baseUrl/_ingest/pipeline/$pipelineName") ?: return false
        return response.size() > 0
    }

    fun getSqlDatasource(name: String): JsonNode = http.getJson("$baseUrl/_plugins/_query/_datasources/$name")

    fun queryPromQlRange(
        datasourceName: String,
        promQl: String,
        startEpochSeconds: Long,
        endEpochSeconds: Long,
        step: String = "60s",
    ): List<PromQlSeries> {
        val query = "source = $datasourceName.query_range('$promQl', $startEpochSeconds, $endEpochSeconds, '$step')"
        val response = http.postJson("$baseUrl/_plugins/_ppl", mapOf("query" to query))
        val schemaNames = response.path("schema").values().map { it.path("name").asString("") }
        val labelsIndex = schemaNames.indexOf("@labels")
        val valueIndex = schemaNames.indexOf("@value")
        return response.path("datarows").values().map { row ->
            val labels = row.path(labelsIndex).properties().asSequence()
                .associate { (key, value) -> key to value.asString("") }
            val values = row.path(valueIndex).values().map { it.asDouble(0.0) }
            PromQlSeries(labels = labels, values = values)
        }
    }

    fun clusterHealthStatus(index: String): String =
        http.getJson("$baseUrl/_cluster/health/$index").path("status").asString("")

    fun indexSettings(index: String): IndexSettings {
        val response = http.getJson("$baseUrl/$index/_settings")
        val firstIndexSettings = response.properties().asSequence().firstOrNull()?.value
            ?.path("settings")?.path("index")
            ?: error("Missing index settings for $index")
        return IndexSettings(
            shards = firstIndexSettings.path("number_of_shards").asInt(-1),
            replicas = firstIndexSettings.path("number_of_replicas").asInt(-1),
        )
    }

    fun refresh(index: String) {
        http.postJson("$baseUrl/$index/_refresh")
    }

    fun count(index: String, query: Map<String, Any>): Long {
        val response = http.postJson("$baseUrl/$index/_count", mapOf("query" to query))
        return response.path("count").asLong(0L)
    }

    fun search(index: String, query: Map<String, Any>, size: Int = 10, aggs: Map<String, Any>? = null): JsonNode {
        val payload = buildMap {
            put("size", size)
            put("query", query)
            if (aggs != null) put("aggs", aggs)
        }
        return http.postJson("$baseUrl/$index/_search", payload)
    }

    fun termsAggregationKeys(
        index: String,
        field: String,
        query: Map<String, Any> = mapOf("match_all" to emptyMap<String, Any>()),
        size: Int = 50,
    ): Set<String> {
        val aggs = mapOf(
            "keys" to mapOf(
                "terms" to mapOf("field" to field, "size" to size),
            ),
        )
        val response = search(index = index, query = query, size = 0, aggs = aggs)
        return response.path("aggregations").path("keys").path("buckets")
            .values()
            .map { it.path("key").asString("") }
            .toSet()
    }

    fun metricAggregationValue(index: String, metricName: String, aggType: String): Double {
        val response = search(
            index = index,
            query = mapOf("term" to mapOf("name" to metricName)),
            size = 0,
            aggs = mapOf("val" to mapOf(aggType to mapOf("field" to "value"))),
        )
        val valueNode = response.path("aggregations").path("val").path("value")
        return if (valueNode.isMissingNode || valueNode.isNull) 0.0 else valueNode.asDouble(0.0)
    }

    data class IndexSettings(val shards: Int, val replicas: Int)

    data class PromQlSeries(val labels: Map<String, String>, val values: List<Double>)
}
