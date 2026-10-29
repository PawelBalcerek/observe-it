package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderLineItemRequest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aReserveOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeConflictError
import com.observeit.observedapp.test.shouldBeNotFoundError
import com.observeit.observedapp.test.shouldBeUnprocessableEntityError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ReserveOrderIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `post reserve transitions order to RESERVED with 200 OK and emits inventory log with MDC correlation`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())

        // when
        val response = orderApi.reserveOrderAndExpect200(created.orderId, checkoutScenario = "happy-path")

        // then
        response shouldBe aReserveOrderResponse(orderId = created.orderId)

        // and
        logCapture.assertLogged(
            level = DEBUG,
            message = "Inventory reserved for sku (1/100)",
            keyValues = mapOf(
                "inventory.sku" to "sku",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 100L,
            ),
            mdc = mapOf(
                "checkout.scenario" to "happy-path",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        logCapture.assertLogged(
            level = INFO,
            message = "Order reserved",
            keyValues = mapOf(
                "order.state" to "RESERVED",
                "order.previous_state" to "CREATED",
            ),
            mdc = mapOf(
                "checkout.scenario" to "happy-path",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
    }

    @Test
    fun `post reserve returns 200 OK and logs warning when SKU is low stock`() {
        // given
        val created = orderApi.createOrderAndExpect201(
            aCreateOrderRequest(items = listOf(aCreateOrderLineItemRequest(sku = "SKU-LOW-STOCK"))),
        )

        // when
        val response = orderApi.reserveOrderAndExpect200(created.orderId, checkoutScenario = "low-stock")

        // then
        response shouldBe aReserveOrderResponse(orderId = created.orderId)

        // and
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Low stock for SKU-LOW-STOCK (1/2)",
            keyValues = mapOf(
                "inventory.sku" to "SKU-LOW-STOCK",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 2L,
            ),
            mdc = mapOf(
                "checkout.scenario" to "low-stock",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        logEvent.throwableProxy shouldBe null
    }

    @Test
    fun `post reserve returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.reserveOrderAndExpect404("non-existent-order", checkoutScenario = "reserve-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertLogged(
            level = WARN,
            message = "Order not found: non-existent-order",
            mdc = mapOf(
                "checkout.scenario" to "reserve-miss",
                "order.id" to "non-existent-order",
            ),
        )
    }

    @Test
    fun `post reserve returns 409 Conflict and logs warning when order state is not CREATED`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())
        orderApi.cancelOrderAndExpect200(created.orderId)

        // when
        val problem = orderApi.reserveOrderAndExpect409(created.orderId, checkoutScenario = "invalid-transition")

        // then
        problem.shouldBeConflictError("Cannot reserve order ${created.orderId} in CANCELLED state")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe CANCELLED

        // and
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Cannot reserve order in CANCELLED state",
            keyValues = mapOf(
                "error.type" to "INVALID_STATE_TRANSITION",
                "order.attempted_action" to "reserve",
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
    fun `post reserve returns 422 Unprocessable Entity and logs warning when SKU is out of stock`() {
        // given
        val created = orderApi.createOrderAndExpect201(
            aCreateOrderRequest(items = listOf(aCreateOrderLineItemRequest(sku = "SKU-OUT-OF-STOCK"))),
        )

        // when
        val problem = orderApi.reserveOrderAndExpect422(created.orderId, checkoutScenario = "out-of-stock")

        // then
        problem.shouldBeUnprocessableEntityError("Inventory out of stock for SKU-OUT-OF-STOCK")

        // and
        val order = orderApi.getOrderAndExpect200(created.orderId)
        order.state shouldBe FAILED

        // and
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Inventory out of stock for SKU-OUT-OF-STOCK",
            keyValues = mapOf(
                "error.type" to "OUT_OF_STOCK",
                "inventory.sku" to "SKU-OUT-OF-STOCK",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 0L,
            ),
            mdc = mapOf(
                "checkout.scenario" to "out-of-stock",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
            ),
        )
        logEvent.throwableProxy shouldBe null
    }
}
