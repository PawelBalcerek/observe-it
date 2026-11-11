package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderResponse
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderLineItemRequest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItemResponse
import com.observeit.observedapp.order.OrderFixtures.anOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.MetricCapture
import com.observeit.observedapp.test.shouldBeConflictError
import com.observeit.observedapp.test.shouldBeGatewayTimeoutError
import com.observeit.observedapp.test.shouldBeUnprocessableEntityError
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CheckoutScenarioIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture(level = DEBUG)

    @JvmField
    @RegisterExtension
    final val metricCapture = MetricCapture()

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
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderReservedTelemetry(created.orderId, checkoutScenario)
        assertCreditCardPaymentTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderShippedTelemetry(created.orderId, checkoutScenario)
        assertOrderRetrievedTelemetry(created.orderId, "SHIPPED", checkoutScenario)
    }

    @Test
    fun `complete LOW_STOCK_WARNING checkout scenario transitions order to SHIPPED and emits correlated logs`() {
        // given
        val checkoutScenario = "low-stock-warning"

        // when - Step 1: POST /api/orders
        val created = orderApi.createOrderAndExpect201(
            request = aCreateOrderRequest(items = listOf(aCreateOrderLineItemRequest(sku = "SKU-LOW-STOCK"))),
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
        order shouldBe anOrderResponse(
            orderId = created.orderId,
            items = listOf(anOrderLineItemResponse(sku = "SKU-LOW-STOCK")),
            state = SHIPPED,
        )

        // and
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)

        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to created.orderId,
            "customer.id" to "customerId",
        )
        val lowStockEvent = logCapture.assertLogged(
            level = WARN,
            message = "Low stock for SKU-LOW-STOCK (1/2)",
            keyValues = mapOf(
                "inventory.sku" to "SKU-LOW-STOCK",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 2L,
            ),
            mdc = expectedMdc,
        )
        lowStockEvent.throwableProxy shouldBe null

        logCapture.assertLogged(
            level = INFO,
            message = "Order reserved",
            keyValues = mapOf(
                "order.state" to "RESERVED",
                "order.previous_state" to "CREATED",
            ),
            mdc = expectedMdc,
        )
        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "CREATED",
                "order.state" to "RESERVED",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.inventory.reservations",
            unit = "{reservation}",
            delta = 1L,
            attributes = mapOf(
                "inventory.sku" to "SKU-LOW-STOCK",
                "inventory.outcome" to "LOW_STOCK_WARNING",
            ),
        )

        assertCreditCardPaymentTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderShippedTelemetry(created.orderId, checkoutScenario)
        assertOrderRetrievedTelemetry(created.orderId, "SHIPPED", checkoutScenario)
    }

    @Test
    fun `OUT_OF_STOCK checkout scenario transitions order to FAILED on reserve and emits warning log`() {
        // given
        val checkoutScenario = "out-of-stock"

        // when - Step 1: POST /api/orders
        val created = orderApi.createOrderAndExpect201(
            request = aCreateOrderRequest(items = listOf(aCreateOrderLineItemRequest(sku = "SKU-OUT-OF-STOCK"))),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 2: POST /api/orders/{orderId}/reserve
        val problem = orderApi.reserveOrderAndExpect422(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // and - Step 3: GET /api/orders/{orderId}
        val order = orderApi.getOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // then
        problem.shouldBeUnprocessableEntityError("Inventory out of stock for SKU-OUT-OF-STOCK")

        // and
        order shouldBe anOrderResponse(
            orderId = created.orderId,
            items = listOf(anOrderLineItemResponse(sku = "SKU-OUT-OF-STOCK")),
            state = FAILED,
        )

        // and
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)

        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to created.orderId,
            "customer.id" to "customerId",
        )
        val outOfStockEvent = logCapture.assertLogged(
            level = WARN,
            message = "Inventory out of stock for SKU-OUT-OF-STOCK",
            keyValues = mapOf(
                "error.type" to "OUT_OF_STOCK",
                "inventory.sku" to "SKU-OUT-OF-STOCK",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 0L,
            ),
            mdc = expectedMdc,
        )
        outOfStockEvent.throwableProxy shouldBe null

        assertOrderRetrievedTelemetry(created.orderId, "FAILED", checkoutScenario)

        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "CREATED",
                "order.state" to "FAILED",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.inventory.reservations",
            unit = "{reservation}",
            delta = 1L,
            attributes = mapOf(
                "inventory.sku" to "SKU-OUT-OF-STOCK",
                "inventory.outcome" to "OUT_OF_STOCK",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.errors",
            unit = "{error}",
            delta = 1L,
            attributes = mapOf("error.type" to "OUT_OF_STOCK"),
        )
    }

    @Test
    fun `PAYMENT_DECLINED checkout scenario transitions order to FAILED on pay and emits warning log`() {
        // given
        val checkoutScenario = "payment-declined"

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
        val problem = orderApi.payOrderAndExpect422(
            orderId = created.orderId,
            request = aPayOrderRequest(paymentMethod = DECLINED_CARD),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 4: GET /api/orders/{orderId}
        val order = orderApi.getOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // then
        problem.shouldBeUnprocessableEntityError("Payment declined for method DECLINED_CARD")

        // and
        order shouldBe anOrderResponse(orderId = created.orderId, state = FAILED)

        // and
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderReservedTelemetry(created.orderId, checkoutScenario)

        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to created.orderId,
            "customer.id" to "customerId",
        )
        val declinedEvent = logCapture.assertLogged(
            level = WARN,
            message = "Payment declined for method DECLINED_CARD",
            keyValues = mapOf(
                "error.type" to "PAYMENT_DECLINED",
                "payment.method" to "DECLINED_CARD",
                "payment.latency_ms" to 50L,
            ),
            mdc = expectedMdc,
        )
        declinedEvent.throwableProxy shouldBe null

        assertOrderRetrievedTelemetry(created.orderId, "FAILED", checkoutScenario)

        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "RESERVED",
                "order.state" to "FAILED",
            ),
        )
        metricCapture.assertHistogramRecorded(
            name = "order.payment.duration",
            unit = "ms",
            count = 1L,
            sum = 50.0,
            attributes = mapOf(
                "payment.method" to "DECLINED_CARD",
                "payment.outcome" to "DECLINED",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.errors",
            unit = "{error}",
            delta = 1L,
            attributes = mapOf("error.type" to "PAYMENT_DECLINED"),
        )
    }

    @Test
    fun `PAYMENT_GATEWAY_TIMEOUT checkout scenario transitions order to FAILED on pay and emits error log`() {
        // given
        val checkoutScenario = "payment-gateway-timeout"

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
        val problem = orderApi.payOrderAndExpect504(
            orderId = created.orderId,
            request = aPayOrderRequest(paymentMethod = TIMEOUT_GATEWAY),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 4: GET /api/orders/{orderId}
        val order = orderApi.getOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // then
        problem.shouldBeGatewayTimeoutError("Payment gateway timed out")

        // and
        order shouldBe anOrderResponse(orderId = created.orderId, state = FAILED)

        // and
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderReservedTelemetry(created.orderId, checkoutScenario)

        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to created.orderId,
            "customer.id" to "customerId",
        )
        val timeoutEvent = logCapture.assertLogged(
            level = ERROR,
            message = "Payment gateway timed out for method TIMEOUT_GATEWAY",
            keyValues = mapOf(
                "error.type" to "GATEWAY_TIMEOUT",
                "payment.method" to "TIMEOUT_GATEWAY",
            ),
            mdc = expectedMdc,
        )
        val latencyMs = timeoutEvent.keyValuePairs.first { it.key == "payment.latency_ms" }.value as Long
        (latencyMs >= 1000L) shouldBe true
        timeoutEvent.throwableProxy.shouldNotBeNull()

        assertOrderRetrievedTelemetry(created.orderId, "FAILED", checkoutScenario)

        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "RESERVED",
                "order.state" to "FAILED",
            ),
        )
        val paymentPoint = metricCapture.assertHistogramRecorded(
            name = "order.payment.duration",
            unit = "ms",
            count = 1L,
            attributes = mapOf(
                "payment.method" to "TIMEOUT_GATEWAY",
                "payment.outcome" to "TIMEOUT",
            ),
        )
        (paymentPoint.sum >= 1000.0) shouldBe true

        metricCapture.assertCounterIncremented(
            name = "order.errors",
            unit = "{error}",
            delta = 1L,
            attributes = mapOf("error.type" to "GATEWAY_TIMEOUT"),
        )
    }

    @Test
    fun `BUYER_CANCELLATION checkout scenario transitions order to CANCELLED and emits cancellation log`() {
        // given
        val checkoutScenario = "buyer-cancellation"

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

        // and - Step 3: POST /api/orders/{orderId}/cancel
        val response = orderApi.cancelOrderAndExpect200(
            orderId = created.orderId,
            request = aCancelOrderRequest(reason = "buyer cancellation request"),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 4: GET /api/orders/{orderId}
        val order = orderApi.getOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // then
        response shouldBe aCancelOrderResponse(orderId = created.orderId, state = CANCELLED)

        // and
        order shouldBe anOrderResponse(orderId = created.orderId, state = CANCELLED)

        // and
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderReservedTelemetry(created.orderId, checkoutScenario)

        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to created.orderId,
            "customer.id" to "customerId",
        )
        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "RESERVED",
                "cancellation.reason" to "buyer cancellation request",
            ),
            mdc = expectedMdc,
        )

        assertOrderRetrievedTelemetry(created.orderId, "CANCELLED", checkoutScenario)

        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "RESERVED",
                "order.state" to "CANCELLED",
            ),
        )
    }

    @Test
    fun `INVALID_STATE_TRANSITION checkout scenario preserves order state and emits warning log without throwable`() {
        // given
        val checkoutScenario = "invalid-state-transition"

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

        // and - Step 3: POST /api/orders/{orderId}/cancel
        orderApi.cancelOrderAndExpect200(
            orderId = created.orderId,
            request = aCancelOrderRequest(reason = "cancelling before invalid pay"),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 4: POST /api/orders/{orderId}/pay (invalid from CANCELLED)
        val problem = orderApi.payOrderAndExpect409(
            orderId = created.orderId,
            request = aPayOrderRequest(),
            checkoutScenario = checkoutScenario,
        )

        // and - Step 5: GET /api/orders/{orderId}
        val order = orderApi.getOrderAndExpect200(
            orderId = created.orderId,
            checkoutScenario = checkoutScenario,
        )

        // then
        problem.shouldBeConflictError("Cannot pay order ${created.orderId} in CANCELLED state")

        // and
        order shouldBe anOrderResponse(orderId = created.orderId, state = CANCELLED)

        // and
        assertOrderCreatedTelemetry(created.orderId, checkoutScenario)
        assertStandardOrderReservedTelemetry(created.orderId, checkoutScenario)

        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to created.orderId,
            "customer.id" to "customerId",
        )
        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "RESERVED",
                "cancellation.reason" to "cancelling before invalid pay",
            ),
            mdc = expectedMdc,
        )

        val invalidTransitionEvent = logCapture.assertLogged(
            level = WARN,
            message = "Cannot pay order in CANCELLED state",
            keyValues = mapOf(
                "error.type" to "INVALID_STATE_TRANSITION",
                "order.attempted_action" to "pay",
            ),
            mdc = expectedMdc,
        )
        invalidTransitionEvent.throwableProxy shouldBe null

        assertOrderRetrievedTelemetry(created.orderId, "CANCELLED", checkoutScenario)

        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "RESERVED",
                "order.state" to "CANCELLED",
            ),
        )

        metricCapture.assertCounterIncremented(
            name = "order.errors",
            unit = "{error}",
            delta = 1L,
            attributes = mapOf("error.type" to "INVALID_STATE_TRANSITION"),
        )
    }

    private fun assertOrderCreatedTelemetry(orderId: String, checkoutScenario: String) {
        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to orderId,
            "customer.id" to "customerId",
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
        metricCapture.assertCounterIncremented(
            name = "orders.created",
            unit = "{order}",
            delta = 1L,
            attributes = mapOf("order.currency" to "USD"),
        )
    }

    private fun assertOrderRetrievedTelemetry(orderId: String, state: String, checkoutScenario: String) {
        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to orderId,
            "customer.id" to "customerId",
        )
        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to state,
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    private fun assertStandardOrderReservedTelemetry(orderId: String, checkoutScenario: String) {
        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to orderId,
            "customer.id" to "customerId",
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
        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "CREATED",
                "order.state" to "RESERVED",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.inventory.reservations",
            unit = "{reservation}",
            delta = 1L,
            attributes = mapOf(
                "inventory.sku" to "sku",
                "inventory.outcome" to "RESERVED",
            ),
        )
    }

    private fun assertCreditCardPaymentTelemetry(orderId: String, checkoutScenario: String) {
        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to orderId,
            "customer.id" to "customerId",
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
        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "RESERVED",
                "order.state" to "PAID",
            ),
        )
        metricCapture.assertHistogramRecorded(
            name = "order.payment.duration",
            unit = "ms",
            count = 1L,
            sum = 50.0,
            attributes = mapOf(
                "payment.method" to "CREDIT_CARD",
                "payment.outcome" to "SUCCESS",
            ),
        )
    }

    private fun assertStandardOrderShippedTelemetry(orderId: String, checkoutScenario: String) {
        val expectedMdc = mapOf(
            "checkout.scenario" to checkoutScenario,
            "order.id" to orderId,
            "customer.id" to "customerId",
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
        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "PAID",
                "order.state" to "SHIPPED",
            ),
        )
    }
}
