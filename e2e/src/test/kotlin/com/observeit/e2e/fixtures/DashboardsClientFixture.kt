package com.observeit.e2e.fixtures

import com.observeit.e2e.config.E2eConfig
import tools.jackson.databind.JsonNode

class DashboardsClientFixture(
    private val http: HttpJsonClient,
    private val baseUrl: String = E2eConfig.DASHBOARDS_BASE_URL,
) {
    fun overallStatusState(): String =
        http.getJson("$baseUrl/api/status").path("status").path("overall").path("state").asString("")

    fun getWorkspace(workspaceId: String): JsonNode = http.getJson(
        url = "$baseUrl/api/workspaces/$workspaceId",
        headers = XSRF_HEADERS,
    )

    fun findSavedObjects(workspaceId: String, types: List<String>): Set<String> {
        val typeQuery = types.joinToString("&") { "type=$it" }
        val response = http.getJson(
            url = "$baseUrl/w/$workspaceId/api/saved_objects/_find?per_page=100&$typeQuery",
            headers = XSRF_HEADERS,
        )
        return response.path("saved_objects")
            .values()
            .map { "${it.path("type").asString("")}:${it.path("id").asString("")}" }
            .toSet()
    }

    fun getSavedObject(workspaceId: String, type: String, id: String): JsonNode = http.getJson(
        url = "$baseUrl/w/$workspaceId/api/saved_objects/$type/$id",
        headers = XSRF_HEADERS,
    )

    private companion object {
        val XSRF_HEADERS = mapOf("osd-xsrf" to "true")
    }
}
