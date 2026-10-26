package com.observeit.observedapp.order.api

import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.test.web.servlet.client.RestTestClient

class OrderApiFixture(private val restTestClient: RestTestClient) {

    fun createOrder(request: CreateOrderRequest, checkoutScenario: String? = null): RestTestClient.ResponseSpec {
        var spec = restTestClient.post()
            .uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
        if (checkoutScenario != null) {
            spec = spec.header("X-Checkout-Scenario", checkoutScenario)
        }
        return spec.exchange()
    }

    fun createOrderAndExpect201(request: CreateOrderRequest, checkoutScenario: String? = null): OrderResponse =
        createOrder(request, checkoutScenario)
            .expectStatus().isCreated
            .expectBody(OrderResponse::class.java)
            .returnResult()
            .responseBody!!

    fun createOrderAndExpect400(request: CreateOrderRequest, checkoutScenario: String? = null): ProblemDetail =
        createOrder(request, checkoutScenario)
            .expectStatus().isBadRequest
            .expectBody(ProblemDetail::class.java)
            .returnResult()
            .responseBody!!
}
