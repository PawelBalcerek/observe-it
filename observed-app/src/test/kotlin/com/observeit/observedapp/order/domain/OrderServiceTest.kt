package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level.DEBUG
import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.FakeOrderRepository
import com.observeit.observedapp.order.FakePaymentGateway
import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
import com.observeit.observedapp.order.assertInvalidStateTransitionSpan
import com.observeit.observedapp.order.assertInventoryReservationIncremented
import com.observeit.observedapp.order.assertInventoryReservationSpanEvent
import com.observeit.observedapp.order.assertInventoryReservedLog
import com.observeit.observedapp.order.assertLowStockWarningLog
import com.observeit.observedapp.order.assertOrderCancelledLog
import com.observeit.observedapp.order.assertOrderCancelledSpan
import com.observeit.observedapp.order.assertOrderCreatedLog
import com.observeit.observedapp.order.assertOrderCreatedSpan
import com.observeit.observedapp.order.assertOrderErrorIncremented
import com.observeit.observedapp.order.assertOrderNotFoundSpan
import com.observeit.observedapp.order.assertOrderOutOfStockSpan
import com.observeit.observedapp.order.assertOrderPaidLog
import com.observeit.observedapp.order.assertOrderPaidSpan
import com.observeit.observedapp.order.assertOrderRepositorySizeGauge
import com.observeit.observedapp.order.assertOrderReservedLog
import com.observeit.observedapp.order.assertOrderReservedSpan
import com.observeit.observedapp.order.assertOrderRetrievedLog
import com.observeit.observedapp.order.assertOrderRetrievedSpan
import com.observeit.observedapp.order.assertOrderShippedLog
import com.observeit.observedapp.order.assertOrderShippedSpan
import com.observeit.observedapp.order.assertOrdersCreatedIncremented
import com.observeit.observedapp.order.assertPaymentDeclinedProcessSpan
import com.observeit.observedapp.order.assertPaymentDeclinedSpan
import com.observeit.observedapp.order.assertPaymentDurationRecorded
import com.observeit.observedapp.order.assertPaymentProcessSpan
import com.observeit.observedapp.order.assertPaymentTimeoutDurationRecorded
import com.observeit.observedapp.order.assertPaymentTimeoutProcessSpan
import com.observeit.observedapp.order.assertPaymentTimeoutSpan
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
import com.observeit.observedapp.order.infrastructure.OtelOrderMetrics
import com.observeit.observedapp.order.infrastructure.OtelOrderTracer
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.MetricCapture
import com.observeit.observedapp.test.SpanCapture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.trace.SpanKind.SERVER
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.MDC
import java.math.BigDecimal

class OrderServiceTest {

    @AfterEach
    fun clearCorrelation() {
        CorrelationContext.clear()
    }

    @JvmField
    @RegisterExtension
    val logCapture = LogCapture(level = DEBUG)

    @JvmField
    @RegisterExtension
    val metricCapture = MetricCapture()

    @JvmField
    @RegisterExtension
    val spanCapture = SpanCapture()

    private val repository = FakeOrderRepository()
    private val paymentGateway = FakePaymentGateway()
    private val orderMetrics = OtelOrderMetrics(metricCapture.openTelemetry, repository)
    private val orderTracer = OtelOrderTracer(spanCapture.openTelemetry)
    private val service = OrderService(repository, paymentGateway, orderMetrics, orderTracer)

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

        // and
        spanCapture.assertOrderCreatedSpan(
            orderId = order.id,
            totalAmount = 37.0,
            itemCount = 5L,
        )
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

        // and
        spanCapture.assertOrderRetrievedSpan(orderId = existingOrder.id)
    }

    @Test
    fun `throws OrderNotFoundException when getting order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.getOrder("non-existent-id")
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")

        // and
        spanCapture.assertOrderNotFoundSpan(spanName = "order.get", orderId = "non-existent-id")
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

        // and
        spanCapture.assertOrderReservedSpan(orderId = existingOrder.id)
        spanCapture.assertInventoryReservationSpanEvent()
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

        // and
        spanCapture.assertOrderReservedSpan(orderId = existingOrder.id)
        spanCapture.assertInventoryReservationSpanEvent(
            sku = "SKU-LOW-STOCK",
            availableQuantity = 2L,
            outcome = LOW_STOCK_WARNING,
        )
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

        // and
        spanCapture.assertOrderOutOfStockSpan(orderId = existingOrder.id, sku = "SKU-OUT-OF-STOCK")
        spanCapture.assertInventoryReservationSpanEvent(
            sku = "SKU-OUT-OF-STOCK",
            availableQuantity = 0L,
            outcome = OUT_OF_STOCK,
        )
    }

    @Test
    fun `throws OrderNotFoundException when reserving order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.reserveOrder("non-existent-id")
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")

        // and
        spanCapture.assertOrderNotFoundSpan(spanName = "order.reserve", orderId = "non-existent-id")
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

        // and
        val paySpan = spanCapture.assertOrderPaidSpan(orderId = existingOrder.id)
        spanCapture.assertPaymentProcessSpan(
            orderId = existingOrder.id,
            traceId = paySpan.traceId,
            parentSpanId = paySpan.spanId,
        )
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

        // and
        val paySpan = spanCapture.assertPaymentDeclinedSpan(orderId = existingOrder.id)
        spanCapture.assertPaymentDeclinedProcessSpan(
            orderId = existingOrder.id,
            traceId = paySpan.traceId,
            parentSpanId = paySpan.spanId,
        )
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

        // and
        val paySpan = spanCapture.assertPaymentTimeoutSpan(orderId = existingOrder.id)
        spanCapture.assertPaymentTimeoutProcessSpan(
            orderId = existingOrder.id,
            traceId = paySpan.traceId,
            parentSpanId = paySpan.spanId,
        )
    }

    @Test
    fun `throws OrderNotFoundException when paying order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.payOrder("non-existent-id", paymentMethod = CREDIT_CARD)
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")

        // and
        spanCapture.assertOrderNotFoundSpan(spanName = "order.pay", orderId = "non-existent-id")
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

        // and
        spanCapture.assertOrderShippedSpan(orderId = existingOrder.id)
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

        // and
        spanCapture.assertOrderCancelledSpan(
            orderId = existingOrder.id,
            previousState = CREATED,
            reason = "buyer changed mind",
        )
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

        // and
        spanCapture.assertOrderCancelledSpan(
            orderId = existingOrder.id,
            previousState = RESERVED,
            reason = "item no longer needed",
        )
    }

    @Test
    fun `throws OrderNotFoundException when cancelling order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.cancelOrder("non-existent-id", reason = "buyer request")
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")

        // and
        spanCapture.assertOrderNotFoundSpan(spanName = "order.cancel", orderId = "non-existent-id")
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

        // and
        spanCapture.assertInvalidStateTransitionSpan(
            spanName = "order.reserve",
            orderId = existingOrder.id,
            attemptedAction = "reserve",
        )
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

        // and
        spanCapture.assertInvalidStateTransitionSpan(
            spanName = "order.pay",
            orderId = existingOrder.id,
            attemptedAction = "pay",
        )
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

        // and
        spanCapture.assertInvalidStateTransitionSpan(
            spanName = "order.ship",
            orderId = existingOrder.id,
            attemptedAction = "ship",
        )
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

        // and
        spanCapture.assertInvalidStateTransitionSpan(
            spanName = "order.cancel",
            orderId = existingOrder.id,
            attemptedAction = "cancel",
        )
    }

    @Test
    fun `throws OrderNotFoundException when shipping order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.shipOrder("non-existent-id", shippingMethod = STANDARD)
        }

        // then
        exception shouldBe OrderNotFoundException("non-existent-id")

        // and
        spanCapture.assertOrderNotFoundSpan(spanName = "order.ship", orderId = "non-existent-id")
    }

    @Test
    fun `enriches active parent SERVER span with customer id during order lookup`() {
        // given
        val existingOrder = repository.save(anOrder())
        val serverSpan = spanCapture.openTelemetry.getTracer("test")
            .spanBuilder("GET /api/orders/{orderId}")
            .setSpanKind(SERVER)
            .startSpan()

        // when
        serverSpan.makeCurrent().use {
            CorrelationContext.setOrderId(existingOrder.id)
            service.getOrder(existingOrder.id)
        }
        serverSpan.end()

        // then
        spanCapture.assertSpanRecorded(
            name = "GET /api/orders/{orderId}",
            kind = SERVER,
            attributes = mapOf(
                "order.id" to existingOrder.id,
                "customer.id" to "customerId",
            ),
        )
        MDC.get("order.id") shouldBe existingOrder.id
        CorrelationContext.getCustomerId() shouldBe "customerId"
    }
}
