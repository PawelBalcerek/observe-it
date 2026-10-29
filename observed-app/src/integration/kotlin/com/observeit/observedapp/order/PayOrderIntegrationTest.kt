package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.correlation.CorrelationContext.CHECKOUT_SCENARIO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.api.PayOrderRequest
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeNotFoundError
import com.observeit.observedapp.test.shouldBeValidationError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource

class PayOrderIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `post pay transitions order to PAID with 200 OK and emits payment log with MDC correlation`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.reserveOrderAndExpect200(created.orderId)

        // when
        val response = orderApi.payOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = "happy-path",
        )

        // then
        response shouldBe aPayOrderResponse(orderId = created.orderId)

        // and
        logCapture.assertLogged(
            level = INFO,
            message = "Order paid",
            keyValues = mapOf(
                "order.state" to "PAID",
                "order.previous_state" to "RESERVED",
                "payment.method" to "CREDIT_CARD",
                "payment.latency_ms" to 50L,
            ),
            mdc = mapOf(
                CHECKOUT_SCENARIO to "happy-path",
                ORDER_ID to created.orderId,
                CUSTOMER_ID to "customerId",
            ),
        )
    }

    @Test
    fun `post pay returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.payOrderAndExpect404("non-existent-order", checkoutScenario = "pay-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertLogged(
            level = WARN,
            message = "Order not found: non-existent-order",
            mdc = mapOf(
                CHECKOUT_SCENARIO to "pay-miss",
                ORDER_ID to "non-existent-order",
            ),
        )
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("invalidPayRequests")
    fun `post pay returns 400 Bad Request when request validation fails`(
        description: String,
        request: PayOrderRequest,
        expectedError: String,
    ) {
        // when
        val problem = orderApi.payOrderAndExpect400("any-order-id", request)

        // then
        problem.shouldBeValidationError(expectedError)
    }

    companion object {
        @JvmStatic
        val invalidPayRequests = listOf(
            Arguments.of(
                "paymentMethod is null",
                aPayOrderRequest(paymentMethod = null),
                "paymentMethod must not be null",
            ),
        )
    }
}
