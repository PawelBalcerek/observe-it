package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.INFO
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.correlation.CorrelationContext.CHECKOUT_SCENARIO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.anOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.test.LogCapture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CheckoutScenarioIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture(level = DEBUG)

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `complete HAPPY_PATH checkout scenario transitions order to SHIPPED and emits correlated logs`() {
        // given
        val checkoutScenario = "happy-path"

        // when - Step 1: POST /api/orders
        val created = orderApi.createOrderAndExpect201(
            request = aCreateOrderRequest(),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 2: POST /api/orders/{orderId}/reserve
        orderApi.reserveOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // and - Step 3: POST /api/orders/{orderId}/pay
        orderApi.payOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // and - Step 4: POST /api/orders/{orderId}/ship
        orderApi.shipOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // and - Step 5: GET /api/orders/{orderId}
        val order = orderApi.getOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // then
        order shouldBe anOrderResponse(orderId = created.orderId, state = SHIPPED)

        // and
        val expectedMdc = mapOf(
            CHECKOUT_SCENARIO to checkoutScenario,
            ORDER_ID to created.orderId,
            CUSTOMER_ID to "customerId",
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order created",
            keyValues = mapOf(
                "order.state" to "CREATED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = DEBUG,
            message = "Inventory reserved for sku (1/100)",
            keyValues = mapOf(
                "inventory.sku" to "sku",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 100L,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order reserved",
            keyValues = mapOf(
                "order.state" to "RESERVED",
                "order.previous_state" to "CREATED",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order paid",
            keyValues = mapOf(
                "order.state" to "PAID",
                "order.previous_state" to "RESERVED",
                "payment.method" to "CREDIT_CARD",
                "payment.latency_ms" to 50L,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order shipped",
            keyValues = mapOf(
                "order.state" to "SHIPPED",
                "order.previous_state" to "PAID",
                "shipping.method" to "STANDARD",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "SHIPPED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }
}
