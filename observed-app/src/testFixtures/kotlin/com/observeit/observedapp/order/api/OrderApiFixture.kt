package com.observeit.observedapp.order.api

import com.observeit.observedapp.correlation.api.MdcCorrelationFilter.Companion.HEADER_CHECKOUT_SCENARIO
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.ProblemDetail
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.RestTestClient.RequestHeadersSpec
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec

class OrderApiFixture(private val restTestClient: RestTestClient) {

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

    fun getOrderAndExpect200(orderId: String, checkoutScenario: String? = null): OrderResponse =
        getOrder(orderId, checkoutScenario)
            .expectStatus().isOk
            .expectBody(OrderResponse::class.java)
            .returnResult()
            .responseBody!!

    fun getOrderAndExpect404(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        getOrder(orderId, checkoutScenario).expectNotFound()

    fun reserveOrderAndExpect200(orderId: String, checkoutScenario: String? = null): ReserveOrderResponse =
        reserveOrder(orderId, checkoutScenario)
            .expectStatus().isOk
            .expectBody(ReserveOrderResponse::class.java)
            .returnResult()
            .responseBody!!

    fun reserveOrderAndExpect404(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        reserveOrder(orderId, checkoutScenario).expectNotFound()

    fun payOrderAndExpect200(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): PayOrderResponse = payOrder(orderId, request, checkoutScenario)
        .expectStatus().isOk
        .expectBody(PayOrderResponse::class.java)
        .returnResult()
        .responseBody!!

    fun payOrderAndExpect404(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario).expectNotFound()

    fun payOrderAndExpect400(
        orderId: String,
        request: PayOrderRequest,
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario)
        .expectStatus().isBadRequest
        .expectBody(ProblemDetail::class.java)
        .returnResult()
        .responseBody!!

    private fun createOrder(request: CreateOrderRequest, checkoutScenario: String? = null): ResponseSpec =
        restTestClient.post()
            .uri("/api/orders")
            .contentType(APPLICATION_JSON)
            .body(request)
            .withCheckoutScenario(checkoutScenario)
            .exchange()

    private fun getOrder(orderId: String, checkoutScenario: String? = null): ResponseSpec = restTestClient.get()
        .uri("/api/orders/{orderId}", orderId)
        .withCheckoutScenario(checkoutScenario)
        .exchange()

    private fun reserveOrder(orderId: String, checkoutScenario: String? = null): ResponseSpec = restTestClient.post()
        .uri("/api/orders/{orderId}/reserve", orderId)
        .withCheckoutScenario(checkoutScenario)
        .exchange()

    private fun payOrder(orderId: String, request: PayOrderRequest, checkoutScenario: String? = null): ResponseSpec =
        restTestClient.post()
            .uri("/api/orders/{orderId}/pay", orderId)
            .contentType(APPLICATION_JSON)
            .body(request)
            .withCheckoutScenario(checkoutScenario)
            .exchange()

    private fun <S : RequestHeadersSpec<S>> RequestHeadersSpec<S>.withCheckoutScenario(checkoutScenario: String?): S =
        if (checkoutScenario != null) {
            header(HEADER_CHECKOUT_SCENARIO, checkoutScenario)
        } else {
            @Suppress("UNCHECKED_CAST")
            this as S
        }

    private fun ResponseSpec.expectNotFound(): ProblemDetail = expectStatus().isNotFound
        .expectBody(ProblemDetail::class.java)
        .returnResult()
        .responseBody!!
}
