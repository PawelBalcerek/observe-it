package com.observeit.e2e.fixtures

import com.observeit.e2e.config.E2eConfig

class TrafficGeneratorClientFixture(
    private val http: HttpJsonClient,
    private val baseUrl: String = E2eConfig.TRAFFIC_GENERATOR_BASE_URL,
) {
    fun triggerScenario(scenario: String): TriggerResponse {
        val body = http.postJson("$baseUrl/api/traffic/trigger/$scenario")
        return TriggerResponse(
            success = body.path("success").asBoolean(false),
            orderId = body.path("orderId").asString(null),
        )
    }

    data class TriggerResponse(val success: Boolean, val orderId: String?)
}
