package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level.DEBUG
import com.observeit.observedapp.order.FakePaymentGateway
import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
import com.observeit.observedapp.order.assertInventoryReservationIncremented
import com.observeit.observedapp.order.assertInventoryReservedLog
import com.observeit.observedapp.order.assertLowStockWarningLog
import com.observeit.observedapp.order.assertOrderCancelledLog
import com.observeit.observedapp.order.assertOrderCreatedLog
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderPaidLog
import com.observeit.observedapp.order.assertOrderRepositorySizeGauge
import com.observeit.observedapp.order.assertOrderReservedLog
import com.observeit.observedapp.order.assertOrderRetrievedLog
import com.observeit.observedapp.order.assertOrderShippedLog
import com.observeit.observedapp.order.assertOrdersCreatedIncremented
import com.observeit.observedapp.order.assertPaymentDurationRecorded
import com.observeit.observedapp.order.assertPaymentTimeoutDurationRecorded
import com.observeit.observedapp.order.assertStateTransitionIncremented
import com.observeit.observedapp.order.domain.InventoryOutcome.LOW_STOCK_WARNING
import com.observeit.observedapp.order.domain.InventoryOutcome.OUT_OF_STOCK
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.order.domain.PaymentOutcome.DECLINED
import com.observeit.observedapp.order.domain.ShippingMethod.STANDARD
import com.observeit.observedapp.order.infrastructure.InMemoryOrderRepository
import com.observeit.observedapp.order.infrastructure.OtelOrderMetrics
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.MetricCapture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal

class OrderServiceTest {

    @JvmField
    @RegisterExtension
    val logCapture = LogCapture(level = DEBUG)

    @JvmField
    @RegisterExtension
    val metricCapture = MetricCapture()

    private val repository = InMemoryOrderRepository()
    private val paymentGateway = FakePaymentGateway()
    private val orderMetrics = OtelOrderMetrics(metricCapture.openTelemetry, repository)
    private val service = OrderService(repository, paymentGateway, orderMetrics)

    @Test
    fun `creates order, populates correlation, stores in repository, and emits structured log`() {
        // given
        val items = listOf(
            anOrderLineItem(quantity = 2, unitPrice = BigDecimal("15.50")),
            anOrderLineItem(quantity = 3, unitPrice = BigDecimal("2.00")),
        )

        // when
        val order = service.createOrder(customerId = "customerId", items = items)

        // then
        order shouldBe anOrder(id = order.id, items = items)

        // and
        repository.findById(order.id) shouldBe order

        // and
        logCapture.assertOrderCreatedLog(
            orderId = order.id,
            totalAmount = 37.0,
            itemCount = 5L,
        )

        // and
        metricCapture.assertOrdersCreatedIncremented()
        metricCapture.assertOrderRepositorySizeGauge(1L)
    }

    @Test
    fun `gets order, populates correlation, and emits structured log`() {
        // given
        val existingOrder = repository.save(anOrder())

        // when
        val order = service.getOrder(existingOrder.id)

        // then
        order shouldBe existingOrder

        // and
        logCapture.assertOrderRetrievedLog(orderId = existingOrder.id)
    }

    @Test
    fun `throws OrderNotFoundException when getting order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.getOrder("non-existent-id")
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")
    }

    @Test
    fun `reserves order, transitions state from CREATED to RESERVED, updates repository, and emits structured log`() {
        // given
        val existingOrder = repository.save(anOrder(state = CREATED))

        // when
        val reserved = service.reserveOrder(existingOrder.id)

        // then
        reserved shouldBe anOrder(id = existingOrder.id, state = RESERVED)

        // and
        repository.findById(existingOrder.id) shouldBe reserved

        // and
        logCapture.assertInventoryReservedLog(orderId = existingOrder.id)

        // and
        logCapture.assertOrderReservedLog(orderId = existingOrder.id)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(outcome = InventoryOutcome.RESERVED)
    }

    @Test
    fun `reserves order with low stock item, emits warning log without throwable, and transitions to RESERVED`() {
        // given
        val items = listOf(anOrderLineItem(sku = "SKU-LOW-STOCK"))
        val existingOrder = repository.save(anOrder(items = items))

        // when
        val reserved = service.reserveOrder(existingOrder.id)

        // then
        reserved shouldBe anOrder(id = existingOrder.id, items = items, state = RESERVED)

        // and
        logCapture.assertLowStockWarningLog(orderId = existingOrder.id)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = RESERVED)
        metricCapture.assertInventoryReservationIncremented(sku = "SKU-LOW-STOCK", outcome = LOW_STOCK_WARNING)
    }

    @Test
    fun `reserves order with out-of-stock item and transitions order to FAILED`() {
        // given
        val items = listOf(anOrderLineItem(sku = "SKU-OUT-OF-STOCK"))
        val existingOrder = repository.save(anOrder(items = items))

        // when
        val exception = shouldThrow<OutOfStockException> {
            service.reserveOrder(existingOrder.id)
        }

        // then
        exception shouldBe OutOfStockException(
            sku = "SKU-OUT-OF-STOCK",
            requestedQuantity = 1L,
            availableQuantity = 0L,
        )

        // and
        repository.findById(existingOrder.id) shouldBe anOrder(id = existingOrder.id, items = items, state = FAILED)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = FAILED)
        metricCapture.assertInventoryReservationIncremented(sku = "SKU-OUT-OF-STOCK", outcome = OUT_OF_STOCK)
        metricCapture.assertOrderErrorIncremented("OUT_OF_STOCK")
    }

    @Test
    fun `throws OrderNotFoundException when reserving order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.reserveOrder("non-existent-id")
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")
    }

    @Test
    fun `pays order, transitions state from RESERVED to PAID, updates repository, and emits structured payment log`() {
        // given
        val existingOrder = repository.save(anOrder(state = RESERVED))

        // when
        val paid = service.payOrder(existingOrder.id, paymentMethod = CREDIT_CARD)

        // then
        paid shouldBe anOrder(id = existingOrder.id, state = PAID)

        // and
        repository.findById(existingOrder.id) shouldBe paid

        // and
        logCapture.assertOrderPaidLog(orderId = existingOrder.id)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = PAID)
        metricCapture.assertPaymentDurationRecorded()
    }

    @Test
    fun `pays order with declined card and transitions order to FAILED`() {
        // given
        val existingOrder = repository.save(anOrder(state = RESERVED))

        // when
        val exception = shouldThrow<PaymentDeclinedException> {
            service.payOrder(existingOrder.id, paymentMethod = DECLINED_CARD)
        }

        // then
        exception shouldBe PaymentDeclinedException(paymentMethod = DECLINED_CARD, latencyMs = 50L)

        // and
        repository.findById(existingOrder.id) shouldBe anOrder(id = existingOrder.id, state = FAILED)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = FAILED)
        metricCapture.assertPaymentDurationRecorded(method = DECLINED_CARD, outcome = DECLINED)
        metricCapture.assertOrderErrorIncremented("PAYMENT_DECLINED")
    }

    @Test
    fun `pays order with timeout gateway and transitions order to FAILED`() {
        // given
        val existingOrder = repository.save(anOrder(state = RESERVED))

        // when
        val exception = shouldThrow<PaymentGatewayTimeoutException> {
            service.payOrder(existingOrder.id, paymentMethod = TIMEOUT_GATEWAY)
        }

        // then
        exception shouldBe PaymentGatewayTimeoutException(
            paymentMethod = TIMEOUT_GATEWAY,
            latencyMs = 1500L,
            cause = exception.cause.shouldNotBeNull(),
        )

        // and
        repository.findById(existingOrder.id) shouldBe anOrder(id = existingOrder.id, state = FAILED)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = FAILED)
        metricCapture.assertPaymentTimeoutDurationRecorded()
        metricCapture.assertOrderErrorIncremented("GATEWAY_TIMEOUT")
    }

    @Test
    fun `throws OrderNotFoundException when paying order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.payOrder("non-existent-id", paymentMethod = CREDIT_CARD)
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")
    }

    @Test
    fun `ships order, transitions state from PAID to SHIPPED, updates repository, and emits structured shipping log`() {
        // given
        val existingOrder = repository.save(anOrder(state = PAID))

        // when
        val shipped = service.shipOrder(existingOrder.id, shippingMethod = STANDARD)

        // then
        shipped shouldBe anOrder(id = existingOrder.id, state = SHIPPED)

        // and
        repository.findById(existingOrder.id) shouldBe shipped

        // and
        logCapture.assertOrderShippedLog(orderId = existingOrder.id)

        // and
        metricCapture.assertStateTransitionIncremented(previousState = PAID, state = SHIPPED)
    }

    @Test
    fun `cancels order in CREATED state, transitions to CANCELLED, updates repository, and emits cancellation log`() {
        // given
        val existingOrder = repository.save(anOrder(state = CREATED))

        // when
        val cancelled = service.cancelOrder(existingOrder.id, reason = "buyer changed mind")

        // then
        cancelled shouldBe anOrder(id = existingOrder.id, state = CANCELLED)

        // and
        repository.findById(existingOrder.id) shouldBe cancelled

        // and
        logCapture.assertOrderCancelledLog(
            orderId = existingOrder.id,
            previousState = CREATED,
            reason = "buyer changed mind",
        )

        // and
        metricCapture.assertStateTransitionIncremented(previousState = CREATED, state = CANCELLED)
    }

    @Test
    fun `cancels order in RESERVED state, transitions to CANCELLED, updates repository, and emits cancellation log`() {
        // given
        val existingOrder = repository.save(anOrder(state = RESERVED))

        // when
        val cancelled = service.cancelOrder(existingOrder.id, reason = "item no longer needed")

        // then
        cancelled shouldBe anOrder(id = existingOrder.id, state = CANCELLED)

        // and
        repository.findById(existingOrder.id) shouldBe cancelled

        // and
        logCapture.assertOrderCancelledLog(
            orderId = existingOrder.id,
            previousState = RESERVED,
            reason = "item no longer needed",
        )

        // and
        metricCapture.assertStateTransitionIncremented(previousState = RESERVED, state = CANCELLED)
    }

    @Test
    fun `throws OrderNotFoundException when cancelling order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.cancelOrder("non-existent-id", reason = "buyer request")
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when reserving order in incompatible state`() {
        // given
        val existingOrder = repository.save(anOrder(state = CANCELLED))

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            service.reserveOrder(existingOrder.id)
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = existingOrder.id,
            state = CANCELLED,
            attemptedAction = "reserve",
        )

        // and
        repository.findById(existingOrder.id) shouldBe existingOrder

        // and
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when paying order in incompatible state`() {
        // given
        val existingOrder = repository.save(anOrder(state = CANCELLED))

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            service.payOrder(existingOrder.id, paymentMethod = CREDIT_CARD)
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = existingOrder.id,
            state = CANCELLED,
            attemptedAction = "pay",
        )

        // and
        repository.findById(existingOrder.id) shouldBe existingOrder

        // and
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when shipping order in incompatible state`() {
        // given
        val existingOrder = repository.save(anOrder(state = CANCELLED))

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            service.shipOrder(existingOrder.id, shippingMethod = STANDARD)
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = existingOrder.id,
            state = CANCELLED,
            attemptedAction = "ship",
        )

        // and
        repository.findById(existingOrder.id) shouldBe existingOrder

        // and
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when cancelling order in incompatible state`() {
        // given
        val existingOrder = repository.save(anOrder(state = SHIPPED))

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            service.cancelOrder(existingOrder.id, reason = "too late")
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = existingOrder.id,
            state = SHIPPED,
            attemptedAction = "cancel",
        )

        // and
        repository.findById(existingOrder.id) shouldBe existingOrder

        // and
        metricCapture.assertOrderErrorIncremented("INVALID_STATE_TRANSITION")
    }

    @Test
    fun `throws OrderNotFoundException when shipping order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.shipOrder("non-existent-id", shippingMethod = STANDARD)
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")
    }
}
