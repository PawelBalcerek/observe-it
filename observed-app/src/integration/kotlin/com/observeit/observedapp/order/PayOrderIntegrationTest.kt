package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.api.PayOrderRequest
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeConflictError
import com.observeit.observedapp.test.shouldBeGatewayTimeoutError
import com.observeit.observedapp.test.shouldBeNotFoundError
import com.observeit.observedapp.test.shouldBeUnprocessableEntityError
import com.observeit.observedapp.test.shouldBeValidationError
import io.kotest.matchers.nulls.shouldNotBeNull
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
                "checkout.scenario" to "happy-path",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
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
                "checkout.scenario" to "pay-miss",
                "order.id" to "non-existent-order",
            ),
        )
    }

    @Test
    fun `post pay returns 409 Conflict and logs warning when order state is not RESERVED`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.cancelOrderAndExpect200(created.orderId)

        // when
        val problem = orderApi.payOrderAndExpect409(
            orderId = created.orderId,
            request = aPayOrderRequest(),
            checkoutScenario = "invalid-transition",
        )

        // then
        problem.shouldBeConflictError("Cannot pay order ${created.orderId} in CANCELLED state")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe CANCELLED

        // and
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Cannot pay order in CANCELLED state",
            keyValues = mapOf(
                "error.type" to "INVALID_STATE_TRANSITION",
                "order.attempted_action" to "pay",
            ),
            mdc = mapOf(
                "checkout.scenario" to "invalid-transition",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        logEvent.throwableProxy shouldBe null
    }

    @Test
    fun `post pay returns 422 Unprocessable Entity and logs warning when payment method is DECLINED_CARD`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.reserveOrderAndExpect200(created.orderId)

        // when
        val problem = orderApi.payOrderAndExpect422(
            orderId = created.orderId,
            request = aPayOrderRequest(paymentMethod = DECLINED_CARD),
            checkoutScenario = "payment-declined",
        )

        // then
        problem.shouldBeUnprocessableEntityError("Payment declined for method DECLINED_CARD")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe FAILED

        // and
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Payment declined for method DECLINED_CARD",
            keyValues = mapOf(
                "error.type" to "PAYMENT_DECLINED",
                "payment.method" to "DECLINED_CARD",
                "payment.latency_ms" to 50L,
            ),
            mdc = mapOf(
                "checkout.scenario" to "payment-declined",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        logEvent.throwableProxy shouldBe null
    }

    @Test
    fun `post pay returns 504 Gateway Timeout and logs error when payment method is TIMEOUT_GATEWAY`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.reserveOrderAndExpect200(created.orderId)

        // when
        val problem = orderApi.payOrderAndExpect504(
            orderId = created.orderId,
            request = aPayOrderRequest(paymentMethod = TIMEOUT_GATEWAY),
            checkoutScenario = "gateway-timeout",
        )

        // then
        problem.shouldBeGatewayTimeoutError("Payment gateway timed out")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe FAILED

        // and
        val logEvent = logCapture.assertLogged(
            level = ERROR,
            message = "Payment gateway timed out for method TIMEOUT_GATEWAY",
            keyValues = mapOf(
                "error.type" to "GATEWAY_TIMEOUT",
                "payment.method" to "TIMEOUT_GATEWAY",
            ),
            mdc = mapOf(
                "checkout.scenario" to "gateway-timeout",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        val latencyMs = logEvent.keyValuePairs.first { it.key == "payment.latency_ms" }.value as Long
        (latencyMs >= 1000L) shouldBe true
        logEvent.throwableProxy.shouldNotBeNull()
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
