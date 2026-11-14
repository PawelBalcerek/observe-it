package com.observeit.observedapp.order

import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aShipOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aShipOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.api.ShipOrderRequest
import com.observeit.observedapp.order.assertInvalidStateTransitionLog
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderNotFoundLog
import com.observeit.observedapp.order.assertOrderShippedLog
import com.observeit.observedapp.order.assertStateTransitionIncremented
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.PAID
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
        logCapture.assertOrderShippedLog(orderId = created.orderId, checkoutScenario = "happy-path")
        metricCapture.assertStateTransitionIncremented(previousState = PAID, state = SHIPPED)
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

    @Test
    fun `post ship returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.shipOrderAndExpect404("non-existent-order", checkoutScenario = "ship-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertOrderNotFoundLog(orderId = "non-existent-order", checkoutScenario = "ship-miss")
    }

    @Test
    fun `post ship returns 409 Conflict and logs warning when order state is not PAID`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.cancelOrderAndExpect200(created.orderId)

        // when
        val problem = orderApi.shipOrderAndExpect409(
            orderId = created.orderId,
            request = aShipOrderRequest(),
            checkoutScenario = "invalid-transition",
        )

        // then
        problem.shouldBeConflictError("Cannot ship order ${created.orderId} in CANCELLED state")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe CANCELLED

        // and
        logCapture.assertInvalidStateTransitionLog(
            orderId = created.orderId,
            state = CANCELLED,
            attemptedAction = "ship",
            checkoutScenario = "invalid-transition",
        )
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
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
