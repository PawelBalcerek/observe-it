package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.FakePaymentGateway
import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.order.domain.ShippingMethod.STANDARD
import com.observeit.observedapp.order.infrastructure.InMemoryOrderRepository
import com.observeit.observedapp.test.LogCapture
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

    private val repository = InMemoryOrderRepository()
    private val paymentGateway = FakePaymentGateway()
    private val service = OrderService(repository, paymentGateway)

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
        logCapture.assertLogged(
            level = INFO,
            message = "Order created",
            keyValues = mapOf(
                "order.state" to "CREATED",
                "order.total_amount" to 37.0,
                "order.currency" to "USD",
                "order.item_count" to 5L,
            ),
            mdc = mapOf(
                ORDER_ID to order.id,
                CUSTOMER_ID to "customerId",
            ),
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "CREATED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )
    }

    @Test
    fun `throws OrderNotFoundException when getting order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.getOrder("non-existent-id")
        }

        // then
        exception.orderId shouldBe "non-existent-id"
        exception.message shouldBe "Order not found: non-existent-id"
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
        logCapture.assertLogged(
            level = DEBUG,
            message = "Inventory reserved for sku (1/100)",
            keyValues = mapOf(
                "inventory.sku" to "sku",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 100L,
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )

        // and
        logCapture.assertLogged(
            level = INFO,
            message = "Order reserved",
            keyValues = mapOf(
                "order.state" to "RESERVED",
                "order.previous_state" to "CREATED",
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )
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
        val logEvent = logCapture.assertLogged(
            level = WARN,
            message = "Low stock for SKU-LOW-STOCK (1/2)",
            keyValues = mapOf(
                "inventory.sku" to "SKU-LOW-STOCK",
                "inventory.requested_quantity" to 1L,
                "inventory.available_quantity" to 2L,
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )
        logEvent.throwableProxy shouldBe null
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order paid",
            keyValues = mapOf(
                "order.state" to "PAID",
                "order.previous_state" to "RESERVED",
                "payment.method" to "CREDIT_CARD",
                "payment.latency_ms" to 50L,
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
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
        exception shouldBe PaymentGatewayTimeoutException(paymentMethod = TIMEOUT_GATEWAY, latencyMs = 1500L)

        // and
        exception.cause.shouldNotBeNull()

        // and
        repository.findById(existingOrder.id) shouldBe anOrder(id = existingOrder.id, state = FAILED)
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order shipped",
            keyValues = mapOf(
                "order.state" to "SHIPPED",
                "order.previous_state" to "PAID",
                "shipping.method" to "STANDARD",
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "CREATED",
                "cancellation.reason" to "buyer changed mind",
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
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
        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "RESERVED",
                "cancellation.reason" to "item no longer needed",
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
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
        repository.findById(existingOrder.id)?.state shouldBe CANCELLED
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
        repository.findById(existingOrder.id)?.state shouldBe CANCELLED
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
        repository.findById(existingOrder.id)?.state shouldBe CANCELLED
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
        repository.findById(existingOrder.id)?.state shouldBe SHIPPED
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
