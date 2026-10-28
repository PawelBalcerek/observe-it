package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level.INFO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.infrastructure.InMemoryOrderRepository
import com.observeit.observedapp.test.LogCapture
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal
import java.util.Currency

class OrderServiceTest {

    @JvmField
    @RegisterExtension
    val logCapture = LogCapture()

    private val repository = InMemoryOrderRepository()
    private val service = OrderService(repository)

    @Test
    fun `creates order, populates correlation, stores in repository, and emits structured log`() {
        // given
        val eur = Currency.getInstance("EUR")
        val items = listOf(
            anOrderLineItem(quantity = 2, unitPrice = BigDecimal("15.50"), currency = eur),
            anOrderLineItem(quantity = 3, unitPrice = BigDecimal("2.00"), currency = eur),
        )

        // when
        val order = service.createOrder(customerId = "custom-customer", items = items)

        // then
        assertSoftly(order) {
            id.shouldNotBeBlank()
            customerId shouldBe "custom-customer"
            state shouldBe CREATED
            items.size shouldBe 2
            totalAmount shouldBe BigDecimal("37.00")
            currency shouldBe eur
            itemCount shouldBe 5L
        }

        // and
        val stored = repository.findById(order.id)
        stored shouldBe order

        // and
        logCapture.assertLogged(
            level = INFO,
            message = "Order created",
            keyValues = mapOf(
                "order.state" to "CREATED",
                "order.total_amount" to 37.0,
                "order.currency" to "EUR",
                "order.item_count" to 5L,
            ),
            mdc = mapOf(
                ORDER_ID to order.id,
                CUSTOMER_ID to "custom-customer",
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
