package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.DEBUG
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aCancelOrderResponse
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderLineItemRequest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aPayOrderRequest
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItemResponse
import com.observeit.observedapp.order.OrderFixtures.anOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.assertInvalidStateTransitionLog
import com.observeit.observedapp.order.assertInventoryReservationIncremented
import com.observeit.observedapp.order.assertInventoryReservedLog
import com.observeit.observedapp.order.assertLowStockWarningLog
import com.observeit.observedapp.order.assertOrderCancelledLog
import com.observeit.observedapp.order.assertOrderCreatedLog
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderPaidLog
import com.observeit.observedapp.order.assertOrderReservedLog
import com.observeit.observedapp.order.assertOrderRetrievedLog
import com.observeit.observedapp.order.assertOrderShippedLog
import com.observeit.observedapp.order.assertOrdersCreatedIncremented
import com.observeit.observedapp.order.assertOutOfStockLog
import com.observeit.observedapp.order.assertPaymentDeclinedLog
import com.observeit.observedapp.order.assertPaymentDurationRecorded
import com.observeit.observedapp.order.assertPaymentGatewayTimeoutLog
import com.observeit.observedapp.order.assertPaymentTimeoutDurationRecorded
import com.observeit.observedapp.order.assertStateTransitionIncremented
import com.observeit.observedapp.order.domain.InventoryOutcome
import com.observeit.observedapp.order.domain.InventoryOutcome.LOW_STOCK_WARNING
import com.observeit.observedapp.order.domain.InventoryOutcome.OUT_OF_STOCK
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.order.domain.PaymentOutcome.DECLINED
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeConflictError
import com.observeit.observedapp.test.shouldBeGatewayTimeoutError
import com.observeit.observedapp.test.shouldBeUnprocessableEntityError
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertInventoryReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)

        logCapture.assertOrderPaidLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = PAID)
        metricCapture.assertPaymentDurationRecorded()

        logCapture.assertOrderShippedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = PAID, state = SHIPPED)

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = SHIPPED,
            checkoutScenario = checkoutScenario,
        )
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertLowStockWarningLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(sku = "SKU-LOW-STOCK", outcome = LOW_STOCK_WARNING)

        logCapture.assertOrderPaidLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = PAID)
        metricCapture.assertPaymentDurationRecorded()

        logCapture.assertOrderShippedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = PAID, state = SHIPPED)

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = SHIPPED,
            checkoutScenario = checkoutScenario,
        )
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertOutOfStockLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = FAILED)
        metricCapture.assertInventoryReservationIncremented(sku = "SKU-OUT-OF-STOCK", outcome = OUT_OF_STOCK)
        metricCapture.assertOrderErrorIncremented("OUT_OF_STOCK")

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = FAILED,
            checkoutScenario = checkoutScenario,
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertInventoryReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)

        logCapture.assertPaymentDeclinedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = FAILED)
        metricCapture.assertPaymentDurationRecorded(method = DECLINED_CARD, outcome = DECLINED)
        metricCapture.assertOrderErrorIncremented("PAYMENT_DECLINED")

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = FAILED,
            checkoutScenario = checkoutScenario,
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertInventoryReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)

        logCapture.assertPaymentGatewayTimeoutLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = FAILED)
        metricCapture.assertPaymentTimeoutDurationRecorded()
        metricCapture.assertOrderErrorIncremented("GATEWAY_TIMEOUT")

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = FAILED,
            checkoutScenario = checkoutScenario,
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertInventoryReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)

        logCapture.assertOrderCancelledLog(
            orderId = created.orderId,
            previousState = RESERVED,
            reason = "buyer cancellation request",
            checkoutScenario = checkoutScenario,
        )
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = CANCELLED)

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = CANCELLED,
            checkoutScenario = checkoutScenario,
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
        logCapture.assertOrderCreatedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertOrdersCreatedIncremented()

        logCapture.assertInventoryReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = checkoutScenario)
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)

        logCapture.assertOrderCancelledLog(
            orderId = created.orderId,
            previousState = RESERVED,
            reason = "cancelling before invalid pay",
            checkoutScenario = checkoutScenario,
        )
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = CANCELLED)

        logCapture.assertInvalidStateTransitionLog(
            orderId = created.orderId,
            state = CANCELLED,
            attemptedAction = "pay",
            checkoutScenario = checkoutScenario,
        )
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")

        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            state = CANCELLED,
            checkoutScenario = checkoutScenario,
        )
    }
}
