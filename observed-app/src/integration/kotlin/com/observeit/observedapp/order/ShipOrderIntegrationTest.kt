package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.correlation.CorrelationContext.CHECKOUT_SCENARIO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aShipOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aShipOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.api.ShipOrderRequest
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeNotFoundError
import com.observeit.observedapp.test.shouldBeValidationError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource

class ShipOrderIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `post ship transitions order to SHIPPED with 200 OK and emits shipping log with MDC correlation`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.reserveOrderAndExpect200(created.orderId)
        orderApi.payOrderAndExpect200(created.orderId)

        // when
        val response = orderApi.shipOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = "happy-path",
        )

        // then
        response shouldBe aShipOrderResponse(orderId = created.orderId)

        // and
        logCapture.assertLogged(
            level = INFO,
            message = "Order shipped",
            keyValues = mapOf(
                "order.state" to "SHIPPED",
                "order.previous_state" to "PAID",
                "shipping.method" to "STANDARD",
            ),
            mdc = mapOf(
                CHECKOUT_SCENARIO to "happy-path",
                ORDER_ID to created.orderId,
                CUSTOMER_ID to "customerId",
            ),
        )
    }

    @Test
    fun `post ship returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.shipOrderAndExpect404("non-existent-order", checkoutScenario = "ship-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertLogged(
            level = WARN,
            message = "Order not found: non-existent-order",
            mdc = mapOf(
                CHECKOUT_SCENARIO to "ship-miss",
                ORDER_ID to "non-existent-order",
            ),
        )
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("invalidShipRequests")
    fun `post ship returns 400 Bad Request when request validation fails`(
        description: String,
        request: ShipOrderRequest,
        expectedError: String,
    ) {
        // when
        val problem = orderApi.shipOrderAndExpect400("any-order-id", request)

        // then
        problem.shouldBeValidationError(expectedError)
    }

    companion object {
        @JvmStatic
        val invalidShipRequests = listOf(
            Arguments.of(
                "shippingMethod is null",
                aShipOrderRequest(shippingMethod = null),
                "shippingMethod must not be null",
            ),
        )
    }
}
