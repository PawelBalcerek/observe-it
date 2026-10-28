package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level.INFO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
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
    val logCapture = LogCapture()

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
}
