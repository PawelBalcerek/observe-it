package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderResponse
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.api.CancelOrderRequest
import com.observeit.observedapp.order.api.OrderApiFixture
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "CREATED",
                "cancellation.reason" to "buyer changed mind",
            ),
            mdc = mapOf(
                "checkout.scenario" to "buyer-cancellation",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "RESERVED",
                "cancellation.reason" to "item no longer needed",
            ),
            mdc = mapOf(
                "checkout.scenario" to "buyer-cancellation",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
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
        logCapture.assertLogged(
            level = WARN,
            message = "Order not found: non-existent-order",
            mdc = mapOf(
                "checkout.scenario" to "cancel-miss",
                "order.id" to "non-existent-order",
            ),
        )
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
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Cannot cancel order in SHIPPED state",
            keyValues = mapOf(
                "error.type" to "INVALID_STATE_TRANSITION",
                "order.attempted_action" to "cancel",
            ),
            mdc = mapOf(
                "checkout.scenario" to "invalid-transition",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        logEvent.throwableProxy shouldBe null
    }

    companion object {
        @JvmStatic
        val invalidCancelRequests = listOf(
            Arguments.of("null reason", aCancelOrderRequest(reason = null), "reason must not be blank"),
            Arguments.of("blank reason", aCancelOrderRequest(reason = "   "), "reason must not be blank"),
        )
    }
}
