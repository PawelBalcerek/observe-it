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
        expectedCustomerId: String = DEFAULT_CUSTOMER_ID,
        returnOrderId: String = DEFAULT_ORDER_ID,
        returnState: String = STATE_CREATED,
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withStatus(CREATED)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "orderId": "$returnOrderId",
                            "customerId": "$expectedCustomerId",
                            "state": "$returnState",
                            "items": [
                                {
                                    "sku": "sku",
                                    "quantity": 1,
                                    "unitPrice": 10.00,
                                    "currency": "USD"
                                }
                            ],
                            "totalAmount": 10.00,
                            "currency": "USD",
                            "createdAt": "2026-10-30T12:00:00Z"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectReserveOrder200(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        returnState: String = STATE_RESERVED,
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/reserve"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
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
        orderId: String = DEFAULT_ORDER_ID,
        detail: String = "Requested quantity exceeds available stock",
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/reserve"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withStatus(UNPROCESSABLE_CONTENT)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "type": "https://observeit.com/errors/out-of-stock",
                            "title": "Unprocessable Content",
                            "status": 422,
                            "detail": "$detail",
                            "instance": "$ORDERS_PATH/$orderId/reserve"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectReserveOrder422OutOfStock(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        detail: String = "Requested quantity exceeds available stock",
    ) = expectReserveOrder422(scenario, orderId, detail)

    fun expectPayOrder200(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "CREDIT_CARD",
        returnState: String = STATE_PAID,
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
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
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "DECLINED_CARD",
        detail: String = "Payment was declined by card issuer",
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"paymentMethod": "$paymentMethod"}"""))
            .andRespond(
                withStatus(UNPROCESSABLE_CONTENT)
                    .contentType(APPLICATION_JSON)
                    .body(
                        """
                        {
                            "type": "https://observeit.com/errors/payment-declined",
                            "title": "Unprocessable Content",
                            "status": 422,
                            "detail": "$detail",
                            "instance": "$ORDERS_PATH/$orderId/pay"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectPayOrder422Declined(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "DECLINED_CARD",
        detail: String = "Payment was declined by card issuer",
    ) = expectPayOrder422(scenario, orderId, paymentMethod, detail)

    fun expectPayOrder504(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "TIMEOUT_GATEWAY",
        detail: String = "Payment gateway timed out after 3000ms",
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
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
                            "instance": "$ORDERS_PATH/$orderId/pay"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectPayOrder504Timeout(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "TIMEOUT_GATEWAY",
        detail: String = "Payment gateway timed out after 3000ms",
    ) = expectPayOrder504(scenario, orderId, paymentMethod, detail)

    fun expectPayOrder409(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "CREDIT_CARD",
        detail: String = "Cannot pay order in CANCELLED state",
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/pay"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
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
                            "instance": "$ORDERS_PATH/$orderId/pay"
                        }
                        """.trimIndent(),
                    ),
            )
    }

    fun expectPayOrder409Conflict(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        paymentMethod: String = "CREDIT_CARD",
        detail: String = "Cannot pay order in CANCELLED state",
    ) = expectPayOrder409(scenario, orderId, paymentMethod, detail)

    fun expectShipOrder200(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        shippingMethod: String = "STANDARD",
        returnState: String = STATE_SHIPPED,
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/ship"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"shippingMethod": "$shippingMethod"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "state": "$returnState",
                        "shippingMethod": "$shippingMethod"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectCancelOrder200(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        reason: String = "buyer cancellation request",
        returnState: String = STATE_CANCELLED,
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/cancel"))
            .andExpect(method(POST))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andExpect(content().json("""{"reason": "$reason"}"""))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "state": "$returnState",
                        "reason": "$reason"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectGetOrder200(
        scenario: CheckoutScenario,
        orderId: String = DEFAULT_ORDER_ID,
        state: String = STATE_SHIPPED,
        customerId: String = DEFAULT_CUSTOMER_ID,
    ) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId"))
            .andExpect(method(GET))
            .andExpect(header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue))
            .andRespond(
                withSuccess(
                    """
                    {
                        "orderId": "$orderId",
                        "customerId": "$customerId",
                        "state": "$state",
                        "items": [
                            {
                                "sku": "sku",
                                "quantity": 1,
                                "unitPrice": 10.00,
                                "currency": "USD"
                            }
                        ],
                        "totalAmount": 10.00,
                        "currency": "USD",
                        "createdAt": "2026-10-30T12:00:00Z"
                    }
                    """.trimIndent(),
                    APPLICATION_JSON,
                ),
            )
    }

    fun expectCreateOrder500() {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectReserveOrder500(orderId: String = DEFAULT_ORDER_ID) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/reserve"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectPayOrder500(orderId: String = DEFAULT_ORDER_ID) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/pay"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectShipOrder500(orderId: String = DEFAULT_ORDER_ID) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/ship"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectCancelOrder500(orderId: String = DEFAULT_ORDER_ID) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId/cancel"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun expectGetOrder500(orderId: String = DEFAULT_ORDER_ID) {
        mockServer.expect(requestTo("$BASE_URL$ORDERS_PATH/$orderId"))
            .andExpect(method(GET))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR))
    }

    fun verify() {
        mockServer.verify()
    }

    companion object {
        const val HEADER_CHECKOUT_SCENARIO = "X-Checkout-Scenario"
        const val BASE_URL = "http://localhost:8080"
        const val ORDERS_PATH = "/api/orders"
        const val DEFAULT_ORDER_ID = "order-123"
        const val DEFAULT_CUSTOMER_ID = "customerId"
        const val STATE_CREATED = "CREATED"
        const val STATE_RESERVED = "RESERVED"
        const val STATE_PAID = "PAID"
        const val STATE_SHIPPED = "SHIPPED"
        const val STATE_CANCELLED = "CANCELLED"
    }
}
