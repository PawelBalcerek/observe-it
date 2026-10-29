package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.OrderFixtures.aCancelOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aShipOrderRequest
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.GATEWAY_TIMEOUT
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.ProblemDetail
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.RestTestClient.RequestHeadersSpec
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec

class OrderApiFixture(private val restTestClient: RestTestClient) {

    fun createOrderAndExpect201(request: CreateOrderRequest, checkoutScenario: String? = null): CreateOrderResponse =
        createOrder(request, checkoutScenario)
            .expectStatus().isCreated
            .expectNonNullBody(CreateOrderResponse::class.java)

    fun createOrderAndExpect400(request: CreateOrderRequest, checkoutScenario: String? = null): ProblemDetail =
        createOrder(request, checkoutScenario).expectBadRequest()

    fun getOrderAndExpect200(orderId: String, checkoutScenario: String? = null): OrderResponse =
        getOrder(orderId, checkoutScenario)
            .expectStatus().isOk
            .expectNonNullBody(OrderResponse::class.java)

    fun getOrderAndExpect404(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        getOrder(orderId, checkoutScenario).expectNotFound()

    fun reserveOrderAndExpect200(orderId: String, checkoutScenario: String? = null): ReserveOrderResponse =
        reserveOrder(orderId, checkoutScenario)
            .expectStatus().isOk
            .expectNonNullBody(ReserveOrderResponse::class.java)

    fun reserveOrderAndExpect404(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        reserveOrder(orderId, checkoutScenario).expectNotFound()

    fun reserveOrderAndExpect409(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        reserveOrder(orderId, checkoutScenario).expectConflict()

    fun reserveOrderAndExpect422(orderId: String, checkoutScenario: String? = null): ProblemDetail =
        reserveOrder(orderId, checkoutScenario).expectUnprocessableEntity()

    fun payOrderAndExpect200(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): PayOrderResponse = payOrder(orderId, request, checkoutScenario)
        .expectStatus().isOk
        .expectNonNullBody(PayOrderResponse::class.java)

    fun payOrderAndExpect404(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario).expectNotFound()

    fun payOrderAndExpect409(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario).expectConflict()

    fun payOrderAndExpect422(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario).expectUnprocessableEntity()

    fun payOrderAndExpect504(
        orderId: String,
        request: PayOrderRequest = aPayOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario).expectGatewayTimeout()

    fun payOrderAndExpect400(
        orderId: String,
        request: PayOrderRequest,
        checkoutScenario: String? = null,
    ): ProblemDetail = payOrder(orderId, request, checkoutScenario).expectBadRequest()

    fun shipOrderAndExpect200(
        orderId: String,
        request: ShipOrderRequest = aShipOrderRequest(),
        checkoutScenario: String? = null,
    ): ShipOrderResponse = shipOrder(orderId, request, checkoutScenario)
        .expectStatus().isOk
        .expectNonNullBody(ShipOrderResponse::class.java)

    fun shipOrderAndExpect404(
        orderId: String,
        request: ShipOrderRequest = aShipOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = shipOrder(orderId, request, checkoutScenario).expectNotFound()

    fun shipOrderAndExpect409(
        orderId: String,
        request: ShipOrderRequest = aShipOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = shipOrder(orderId, request, checkoutScenario).expectConflict()

    fun shipOrderAndExpect400(
        orderId: String,
        request: ShipOrderRequest,
        checkoutScenario: String? = null,
    ): ProblemDetail = shipOrder(orderId, request, checkoutScenario).expectBadRequest()

    fun cancelOrderAndExpect200(
        orderId: String,
        request: CancelOrderRequest = aCancelOrderRequest(),
        checkoutScenario: String? = null,
    ): CancelOrderResponse = cancelOrder(orderId, request, checkoutScenario)
        .expectStatus().isOk
        .expectNonNullBody(CancelOrderResponse::class.java)

    fun cancelOrderAndExpect404(
        orderId: String,
        request: CancelOrderRequest = aCancelOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = cancelOrder(orderId, request, checkoutScenario).expectNotFound()

    fun cancelOrderAndExpect400(
        orderId: String,
        request: CancelOrderRequest,
        checkoutScenario: String? = null,
    ): ProblemDetail = cancelOrder(orderId, request, checkoutScenario).expectBadRequest()

    fun cancelOrderAndExpect409(
        orderId: String,
        request: CancelOrderRequest = aCancelOrderRequest(),
        checkoutScenario: String? = null,
    ): ProblemDetail = cancelOrder(orderId, request, checkoutScenario).expectConflict()

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

    private fun shipOrder(orderId: String, request: ShipOrderRequest, checkoutScenario: String? = null): ResponseSpec =
        restTestClient.post()
            .uri("/api/orders/{orderId}/ship", orderId)
            .contentType(APPLICATION_JSON)
            .body(request)
            .withCheckoutScenario(checkoutScenario)
            .exchange()

    private fun cancelOrder(
        orderId: String,
        request: CancelOrderRequest,
        checkoutScenario: String? = null,
    ): ResponseSpec = restTestClient.post()
        .uri("/api/orders/{orderId}/cancel", orderId)
        .contentType(APPLICATION_JSON)
        .body(request)
        .withCheckoutScenario(checkoutScenario)
        .exchange()

    private fun <S : RequestHeadersSpec<S>> RequestHeadersSpec<S>.withCheckoutScenario(checkoutScenario: String?): S =
        if (checkoutScenario != null) {
            header("X-Checkout-Scenario", checkoutScenario)
        } else {
            @Suppress("UNCHECKED_CAST")
            this as S
        }

    private fun ResponseSpec.expectNotFound(): ProblemDetail =
        expectStatus().isNotFound.expectNonNullBody(ProblemDetail::class.java)

    private fun ResponseSpec.expectBadRequest(): ProblemDetail =
        expectStatus().isBadRequest.expectNonNullBody(ProblemDetail::class.java)

    private fun ResponseSpec.expectConflict(): ProblemDetail =
        expectStatus().isEqualTo(CONFLICT).expectNonNullBody(ProblemDetail::class.java)

    private fun ResponseSpec.expectUnprocessableEntity(): ProblemDetail =
        expectStatus().isEqualTo(UNPROCESSABLE_CONTENT).expectNonNullBody(ProblemDetail::class.java)

    private fun ResponseSpec.expectGatewayTimeout(): ProblemDetail =
        expectStatus().isEqualTo(GATEWAY_TIMEOUT).expectNonNullBody(ProblemDetail::class.java)

    private fun <T : Any> ResponseSpec.expectNonNullBody(bodyType: Class<T>): T =
        checkNotNull(expectBody(bodyType).returnResult().responseBody) {
            "Expected non-null ${bodyType.simpleName} response body"
        }
}
