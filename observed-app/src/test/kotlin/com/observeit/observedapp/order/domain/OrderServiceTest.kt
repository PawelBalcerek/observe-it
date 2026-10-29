package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.INFO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.ShippingMethod.STANDARD
import com.observeit.observedapp.order.infrastructure.InMemoryOrderRepository
import com.observeit.observedapp.test.LogCapture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal

class OrderServiceTest {

    @JvmField
    @RegisterExtension
    val logCapture = LogCapture(level = DEBUG)

    private val repository = InMemoryOrderRepository()
    private val service = OrderService(repository)

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
    fun `gets order by id, populates customer correlation, and emits structured log`() {
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
    fun `throws OrderNotFoundException when order does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.getOrder("non-existent-id")
        }

        // then
        exception.orderId shouldBe "non-existent-id"
        exception.message shouldBe "Order not found: non-existent-id"
    }

    @Test
    fun `reserves order, transitions state to RESERVED, updates repository, and emits structured inventory logs`() {
        // given
        val items = listOf(
            anOrderLineItem(sku = "SKU-1", quantity = 2),
            anOrderLineItem(sku = "SKU-2", quantity = 3),
        )
        val existingOrder = repository.save(anOrder(items = items))

        // when
        val reserved = service.reserveOrder(existingOrder.id)

        // then
        reserved shouldBe anOrder(id = existingOrder.id, items = items, state = RESERVED)

        // and
        repository.findById(existingOrder.id) shouldBe reserved

        // and
        logCapture.assertLogged(
            level = DEBUG,
            message = "Inventory reserved for SKU-1 (2/100)",
            keyValues = mapOf(
                "inventory.sku" to "SKU-1",
                "inventory.requested_quantity" to 2L,
                "inventory.available_quantity" to 100L,
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )
        logCapture.assertLogged(
            level = DEBUG,
            message = "Inventory reserved for SKU-2 (3/100)",
            keyValues = mapOf(
                "inventory.sku" to "SKU-2",
                "inventory.requested_quantity" to 3L,
                "inventory.available_quantity" to 100L,
            ),
            mdc = mapOf(
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
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
                ORDER_ID to existingOrder.id,
                CUSTOMER_ID to existingOrder.customerId,
            ),
        )
    }

    @Test
    fun `throws OrderNotFoundException when reserving order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.reserveOrder("non-existent-id")
        }

        // then
        exception.orderId shouldBe "non-existent-id"
        exception.message shouldBe "Order not found: non-existent-id"
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
    fun `throws OrderNotFoundException when paying order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.payOrder("non-existent-id", paymentMethod = CREDIT_CARD)
        }

        // then
        exception.orderId shouldBe "non-existent-id"
        exception.message shouldBe "Order not found: non-existent-id"
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
    fun `throws OrderNotFoundException when shipping order that does not exist`() {
        // when
        val exception = shouldThrow<OrderNotFoundException> {
            service.shipOrder("non-existent-id", shippingMethod = STANDARD)
        }

        // then
        exception.orderId shouldBe "non-existent-id"
        exception.message shouldBe "Order not found: non-existent-id"
    }
}
