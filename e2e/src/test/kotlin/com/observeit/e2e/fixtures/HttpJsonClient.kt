package com.observeit.e2e.fixtures

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

class HttpJsonClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val mapper: JsonMapper = jacksonMapperBuilder().build(),
) {
    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonNode {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
        headers.forEach { (key, value) -> builder.header(key, value) }
        return executeAndParse(builder.build())
    }

    fun getJsonOrNull(url: String, headers: Map<String, String> = emptyMap()): JsonNode? {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
        headers.forEach { (key, value) -> builder.header(key, value) }
        val response = httpClient.send(builder.build(), BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        check(response.statusCode() in 200..299) {
            "HTTP GET $url failed with status ${response.statusCode()}: ${response.body()}"
        }
        return mapper.readTree(response.body())
    }

    fun postJson(url: String, body: Any? = null): JsonNode {
        val publisher = if (body == null) {
            BodyPublishers.noBody()
        } else {
            BodyPublishers.ofString(mapper.writeValueAsString(body))
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(publisher)
            .build()
        return executeAndParse(request)
    }

    private fun executeAndParse(request: HttpRequest): JsonNode {
        val response = httpClient.send(request, BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "HTTP ${request.method()} ${request.uri()} failed with status ${response.statusCode()}: ${response.body()}"
        }
        return mapper.readTree(response.body())
    }
}
