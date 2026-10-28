package com.observeit.observedapp.order.api

import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.ProblemDetail
import org.springframework.test.web.servlet.client.RestTestClient

class OrderApiFixture(private val restTestClient: RestTestClient) {

    fun createOrder(request: CreateOrderRequest, checkoutScenario: String? = null): RestTestClient.ResponseSpec {
        var spec = restTestClient.post()
            .uri("/api/orders")
            .contentType(APPLICATION_JSON)
            .body(request)
        if (checkoutScenario != null) {
            spec = spec.header("X-Checkout-Scenario", checkoutScenario)
        }
        return spec.exchange()
    }

    fun createOrderAndExpect201(request: CreateOrderRequest, checkoutScenario: String? = null): CreateOrderResponse =
        createOrder(request, checkoutScenario)
            .expectStatus().isCreated
            .expectBody(CreateOrderResponse::class.java)
            .returnResult()
            .responseBody!!

    fun createOrderAndExpect400(request: CreateOrderRequest, checkoutScenario: String? = null): ProblemDetail =
        createOrder(request, checkoutScenario)
            .expectStatus().isBadRequest
            .expectBody(ProblemDetail::class.java)
            .returnResult()
            .responseBody!!

    fun getOrder(orderId: String, checkoutScenario: String? = null): RestTestClient.ResponseSpec {
        var spec = restTestClient.get()
            .uri("/api/orders/{orderId}", orderId)
        if (checkoutScenario != null) {
            spec = spec.header("X-Checkout-Scenario", checkoutScenario)
        }
        return spec.exchange()
    }

    fun getOrderAndExpect200(orderId: String, checkoutScenario: String? = null): OrderResponse =
        getOrder(orderId, checkoutScenario)
            .expectStatus().isOk
            .expectBody(OrderResponse::class.java)
            .returnResult()
            .responseBody!!

    fun getOrderAndExpect404(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        getOrder(orderId, checkoutScenario)
            .expectStatus().isNotFound
            .expectBody(ProblemDetail::class.java)
            .returnResult()
            .responseBody!!
}
