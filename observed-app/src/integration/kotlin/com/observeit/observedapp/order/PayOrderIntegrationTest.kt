package com.observeit.observedapp.order

import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.api.PayOrderRequest
import com.observeit.observedapp.order.assertInvalidStateTransitionLog
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderNotFoundLog
import com.observeit.observedapp.order.assertOrderPaidLog
import com.observeit.observedapp.order.assertPaymentDeclinedLog
import com.observeit.observedapp.order.assertPaymentDurationRecorded
import com.observeit.observedapp.order.assertPaymentGatewayTimeoutLog
import com.observeit.observedapp.order.assertPaymentTimeoutDurationRecorded
import com.observeit.observedapp.order.assertStateTransitionIncremented
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.order.domain.PaymentOutcome.DECLINED
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeConflictError
import com.observeit.observedapp.test.shouldBeGatewayTimeoutError
import com.observeit.observedapp.test.shouldBeNotFoundError
import com.observeit.observedapp.test.shouldBeUnprocessableEntityError
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
        logCapture.assertOrderPaidLog(orderId = created.orderId, checkoutScenario = "happy-path")
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = PAID)
        metricCapture.assertPaymentDurationRecorded()
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
        logCapture.assertOrderNotFoundLog(orderId = "non-existent-order", checkoutScenario = "pay-miss")
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
        logCapture.assertInvalidStateTransitionLog(
            orderId = created.orderId,
            state = CANCELLED,
            attemptedAction = "pay",
            checkoutScenario = "invalid-transition",
        )
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
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
        logCapture.assertPaymentDeclinedLog(orderId = created.orderId, checkoutScenario = "payment-declined")
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = FAILED)
        metricCapture.assertPaymentDurationRecorded(method = DECLINED_CARD, outcome = DECLINED)
        metricCapture.assertOrderErrorIncremented("PAYMENT_DECLINED")
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
        logCapture.assertPaymentGatewayTimeoutLog(orderId = created.orderId, checkoutScenario = "gateway-timeout")
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = FAILED)
        metricCapture.assertPaymentTimeoutDurationRecorded()
        metricCapture.assertOrderErrorIncremented("GATEWAY_TIMEOUT")
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
