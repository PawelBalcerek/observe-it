package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.correlation.CorrelationContext.CHECKOUT_SCENARIO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aReserveOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeNotFoundError
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
                CHECKOUT_SCENARIO to "happy-path",
                ORDER_ID to created.orderId,
                CUSTOMER_ID to "customerId",
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
                CHECKOUT_SCENARIO to "happy-path",
                ORDER_ID to created.orderId,
                CUSTOMER_ID to "customerId",
            ),
        )
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
                CHECKOUT_SCENARIO to "reserve-miss",
                ORDER_ID to "non-existent-order",
            ),
        )
    }
}
