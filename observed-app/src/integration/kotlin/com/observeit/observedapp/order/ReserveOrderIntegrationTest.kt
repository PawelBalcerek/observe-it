package com.observeit.observedapp.order

import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderLineItemRequest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.aReserveOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.assertInvalidStateTransitionLog
import com.observeit.observedapp.order.assertInventoryReservationIncremented
import com.observeit.observedapp.order.assertInventoryReservedLog
import com.observeit.observedapp.order.assertLowStockWarningLog
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderNotFoundLog
import com.observeit.observedapp.order.assertOrderReservedLog
import com.observeit.observedapp.order.assertOutOfStockLog
import com.observeit.observedapp.order.assertStateTransitionIncremented
import com.observeit.observedapp.order.domain.InventoryOutcome
import com.observeit.observedapp.order.domain.InventoryOutcome.LOW_STOCK_WARNING
import com.observeit.observedapp.order.domain.InventoryOutcome.OUT_OF_STOCK
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.RESERVED
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
        logCapture.assertInventoryReservedLog(orderId = created.orderId, checkoutScenario = "happy-path")
        logCapture.assertOrderReservedLog(orderId = created.orderId, checkoutScenario = "happy-path")
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)
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
        logCapture.assertLowStockWarningLog(orderId = created.orderId, checkoutScenario = "low-stock")
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(sku = "SKU-LOW-STOCK", outcome = LOW_STOCK_WARNING)
    }

    @Test
    fun `post reserve returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.reserveOrderAndExpect404("non-existent-order", checkoutScenario = "reserve-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertOrderNotFoundLog(orderId = "non-existent-order", checkoutScenario = "reserve-miss")
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
        logCapture.assertInvalidStateTransitionLog(
            orderId = created.orderId,
            state = CANCELLED,
            attemptedAction = "reserve",
            checkoutScenario = "invalid-transition",
        )
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
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
        logCapture.assertOutOfStockLog(orderId = created.orderId, checkoutScenario = "out-of-stock")
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = FAILED)
        metricCapture.assertInventoryReservationIncremented(sku = "SKU-OUT-OF-STOCK", outcome = OUT_OF_STOCK)
        metricCapture.assertOrderErrorIncremented("OUT_OF_STOCK")
    }
}
