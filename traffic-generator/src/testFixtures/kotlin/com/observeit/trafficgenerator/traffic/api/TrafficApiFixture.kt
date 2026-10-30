package com.observeit.trafficgenerator.traffic.api

import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec

class TrafficApiFixture(private val restTestClient: RestTestClient) {

    fun getStatusAndExpect200(): TrafficStatusResponse = restTestClient.get()
        .uri(Endpoint.TRAFFIC_STATUS_PATH)
        .exchange()
        .expectStatus().isOk
        .expectNonNullBody(TrafficStatusResponse::class.java)

    private fun <T : Any> ResponseSpec.expectNonNullBody(bodyType: Class<T>): T =
        checkNotNull(expectBody(bodyType).returnResult().responseBody) {
            "Expected non-null ${bodyType.simpleName} response body"
        }

    private companion object {
        object Endpoint {
            const val TRAFFIC_STATUS_PATH = "/api/traffic/status"
        }
    }
}
