package com.observeit.observedapp.order

import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderResponse
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.api.CancelOrderRequest
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.assertInvalidStateTransitionLog
import com.observeit.observedapp.order.assertOrderCancelledLog
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderNotFoundLog
import com.observeit.observedapp.order.assertStateTransitionIncremented
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeConflictError
import com.observeit.observedapp.test.shouldBeNotFoundError
import com.observeit.observedapp.test.shouldBeValidationError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource

class CancelOrderIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `post cancel transitions order from CREATED to CANCELLED with 200 OK and emits cancellation log`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())

        // when
        val response = orderApi.cancelOrderAndExpect200(
            orderId = created.orderId,
            request = aCancelOrderRequest(reason = "buyer changed mind"),
            checkoutScenario = "buyer-cancellation",
        )

        // then
        response shouldBe aCancelOrderResponse(orderId = created.orderId)

        // and
        logCapture.assertOrderCancelledLog(
            orderId = created.orderId,
            previousState = CREATED,
            reason = "buyer changed mind",
            checkoutScenario = "buyer-cancellation",
        )
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = CANCELLED)
    }

    @Test
    fun `post cancel transitions order from RESERVED to CANCELLED with 200 OK and emits cancellation log`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.reserveOrderAndExpect200(created.orderId)

        // when
        val response = orderApi.cancelOrderAndExpect200(
            orderId = created.orderId,
            request = aCancelOrderRequest(reason = "item no longer needed"),
            checkoutScenario = "buyer-cancellation",
        )

        // then
        response shouldBe aCancelOrderResponse(orderId = created.orderId)

        // and
        logCapture.assertOrderCancelledLog(
            orderId = created.orderId,
            previousState = RESERVED,
            reason = "item no longer needed",
            checkoutScenario = "buyer-cancellation",
        )
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = CANCELLED)
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("invalidCancelRequests")
    fun `post cancel returns 400 Bad Request when request validation fails`(
        description: String,
        request: CancelOrderRequest,
        expectedError: String,
    ) {
        // when
        val problem = orderApi.cancelOrderAndExpect400("any-order-id", request)

        // then
        problem.shouldBeValidationError(expectedError)
    }

    @Test
    fun `post cancel returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.cancelOrderAndExpect404("non-existent-order", checkoutScenario = "cancel-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertOrderNotFoundLog(orderId = "non-existent-order", checkoutScenario = "cancel-miss")
    }

    @Test
    fun `post cancel returns 409 Conflict when order state is incompatible`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.reserveOrderAndExpect200(created.orderId)
        orderApi.payOrderAndExpect200(created.orderId)
        orderApi.shipOrderAndExpect200(created.orderId)

        // when
        val problem = orderApi.cancelOrderAndExpect409(
            orderId = created.orderId,
            request = aCancelOrderRequest(reason = "too late to cancel"),
            checkoutScenario = "invalid-transition",
        )

        // then
        problem.shouldBeConflictError("Cannot cancel order ${created.orderId} in SHIPPED state")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe SHIPPED

        // and
        logCapture.assertInvalidStateTransitionLog(
            orderId = created.orderId,
            state = SHIPPED,
            attemptedAction = "cancel",
            checkoutScenario = "invalid-transition",
        )
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
    }

    companion object {
        @JvmStatic
        val invalidCancelRequests = listOf(
            Arguments.of("null reason", aCancelOrderRequest(reason = null), "reason must not be blank"),
            Arguments.of("blank reason", aCancelOrderRequest(reason = "   "), "reason must not be blank"),
        )
    }
}
