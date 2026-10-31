package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.GATEWAY_TIMEOUT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class ObservedAppMockFixture(private val mockServer: MockRestServiceServer) {

    fun expectCreateOrder(
        scenario: CheckoutScenario,
        returnOrderId: String = Default.ORDER_ID,
        returnState: String = State.CREATED,
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withStatus(CREATED)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "orderId": "$returnOrderId",
                            "state": "$returnState"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectReserveOrder200(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        returnState: String = State.RESERVED,
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/reserve"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "state": "$returnState"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectReserveOrder422(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        detail: String = "Requested quantity exceeds available stock",
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/reserve"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withStatus(UNPROCESSABLE_CONTENT)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "type": "https://observeit.com/errors/out-of-stock",
                            "title": "$TITLE_UNPROCESSABLE_CONTENT",
                            "status": 422,
                            "detail": "$detail",
                            "instance": "${Endpoint.ORDERS_PATH}/$orderId/reserve"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectPayOrder200(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        paymentMethod: String = Default.PAYMENT_CREDIT_CARD,
        returnState: String = State.PAID,
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"paymentMethod": "$paymentMethod"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "state": "$returnState"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectPayOrder422(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        paymentMethod: String = "DECLINED_CARD",
        detail: String = "Payment was declined by card issuer",
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"paymentMethod": "$paymentMethod"}"""))
            .andRespond(
                withStatus(UNPROCESSABLE_CONTENT)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "type": "https://observeit.com/errors/payment-declined",
                            "title": "$TITLE_UNPROCESSABLE_CONTENT",
                            "status": 422,
                            "detail": "$detail",
                            "instance": "${Endpoint.ORDERS_PATH}/$orderId/pay"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectPayOrder504(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        paymentMethod: String = "TIMEOUT_GATEWAY",
        detail: String = "Payment gateway timed out after 3000ms",
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"paymentMethod": "$paymentMethod"}"""))
            .andRespond(
                withStatus(GATEWAY_TIMEOUT)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "type": "https://observeit.com/errors/payment-gateway-timeout",
                            "title": "Gateway Timeout",
                            "status": 504,
                            "detail": "$detail",
                            "instance": "${Endpoint.ORDERS_PATH}/$orderId/pay"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectPayOrder409(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        paymentMethod: String = Default.PAYMENT_CREDIT_CARD,
        detail: String = "Cannot pay order in CANCELLED state",
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"paymentMethod": "$paymentMethod"}"""))
            .andRespond(
                withStatus(CONFLICT)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "type": "https://observeit.com/errors/invalid-state-transition",
                            "title": "Conflict",
                            "status": 409,
                            "detail": "$detail",
                            "instance": "${Endpoint.ORDERS_PATH}/$orderId/pay"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectShipOrder200(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        shippingMethod: String = "STANDARD",
        returnState: String = State.SHIPPED,
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/ship"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"shippingMethod": "$shippingMethod"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "state": "$returnState"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectCancelOrder200(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        reason: String = "buyer cancellation request",
        returnState: String = State.CANCELLED,
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/cancel"))
            .andExpect(method(POST))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"reason": "$reason"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "state": "$returnState"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectGetOrder200(
        scenario: CheckoutScenario,
        orderId: String = Default.ORDER_ID,
        state: String = State.SHIPPED,
        customerId: String = Default.CUSTOMER_ID,
    ) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId"))
            .andExpect(method(GET))
            .andExpect(header(Endpoint.HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "customerId": "$customerId",
                        "state": "$state",
                        "items": [
                            {
                                "sku": "${Default.SKU}",
                                "quantity": 1,
                                "unitPrice": 10.00,
                                "currency": "${Default.CURRENCY}"
                            }
                        ],
                        "totalAmount": 10.00,
                        "currency": "${Default.CURRENCY}",
                        "createdAt": "${Default.CREATED_AT}"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectCreateOrder500() {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectReserveOrder500(orderId: String = Default.ORDER_ID) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/reserve"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectPayOrder500(orderId: String = Default.ORDER_ID) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/pay"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectShipOrder500(orderId: String = Default.ORDER_ID) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/ship"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectCancelOrder500(orderId: String = Default.ORDER_ID) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId/cancel"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectGetOrder500(orderId: String = Default.ORDER_ID) {
        mockServer.expect(requestTo("${Endpoint.BASE_URL}${Endpoint.ORDERS_PATH}/$orderId"))
            .andExpect(method(GET))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun verify() {
        mockServer.verify()
    }

    private companion object {
        object Endpoint {
            const val HEADER_CHECKOUT_SCENARIO = "X-Checkout-Scenario"
            const val BASE_URL = "http://localhost:8080"
            const val ORDERS_PATH = "/api/orders"
        }

        object State {
            const val CREATED = "CREATED"
            const val RESERVED = "RESERVED"
            const val PAID = "PAID"
            const val SHIPPED = "SHIPPED"
            const val CANCELLED = "CANCELLED"
        }

        object Default {
            const val ORDER_ID = "order-123"
            const val CUSTOMER_ID = "customerId"
            const val SKU = "sku"
            const val CURRENCY = "USD"
            const val CREATED_AT = "2026-10-30T12:00:00Z"
            const val PAYMENT_CREDIT_CARD = "CREDIT_CARD"
        }

        const val TITLE_UNPROCESSABLE_CONTENT = "Unprocessable Content"
    }
}
