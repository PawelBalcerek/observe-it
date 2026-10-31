package com.observeit.trafficgenerator.traffic.api

import org.springframework.http.ProblemDetail
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec

class TrafficApiFixture(private val restTestClient: RestTestClient) {

    fun getStatusAndExpect200(): TrafficStatusResponse = restTestClient.get()
        .uri(Endpoint.TRAFFIC_STATUS_PATH)
        .exchange()
        .expectStatus().isOk
        .expectNonNullBody(TrafficStatusResponse::class.java)

    fun pauseAndExpect200(): TrafficStatusResponse = restTestClient.post()
        .uri(Endpoint.TRAFFIC_PAUSE_PATH)
        .exchange()
        .expectStatus().isOk
        .expectNonNullBody(TrafficStatusResponse::class.java)

    fun resumeAndExpect200(): TrafficStatusResponse = restTestClient.post()
        .uri(Endpoint.TRAFFIC_RESUME_PATH)
        .exchange()
        .expectStatus().isOk
        .expectNonNullBody(TrafficStatusResponse::class.java)

    fun updateRateAndExpect200(request: UpdateTrafficRateRequest): UpdateTrafficRateResponse = restTestClient.post()
        .uri(Endpoint.TRAFFIC_RATE_PATH)
        .body(request)
        .exchange()
        .expectStatus().isOk
        .expectNonNullBody(UpdateTrafficRateResponse::class.java)

    fun updateRateAndExpect400(request: UpdateTrafficRateRequest): ProblemDetail = restTestClient.post()
        .uri(Endpoint.TRAFFIC_RATE_PATH)
        .body(request)
        .exchange()
        .expectStatus().isBadRequest
        .expectNonNullBody(ProblemDetail::class.java)

    private fun <T : Any> ResponseSpec.expectNonNullBody(bodyType: Class<T>): T =
        checkNotNull(expectBody(bodyType).returnResult().responseBody) {
            "Expected non-null ${bodyType.simpleName} response body"
        }

    private companion object {
        object Endpoint {
            const val TRAFFIC_STATUS_PATH = "/api/traffic/status"
            const val TRAFFIC_PAUSE_PATH = "/api/traffic/pause"
            const val TRAFFIC_RESUME_PATH = "/api/traffic/resume"
            const val TRAFFIC_RATE_PATH = "/api/traffic/rate"
        }
    }
}
