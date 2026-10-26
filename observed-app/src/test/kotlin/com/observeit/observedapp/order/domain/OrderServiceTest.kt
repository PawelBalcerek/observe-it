package com.observeit.observedapp.order.domain

import ch.qos.logback.classic.Level
import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.OrderFixtures.anOrderLineItem
import com.observeit.observedapp.order.infrastructure.InMemoryOrderRepository
import com.observeit.observedapp.test.LogCapture
import io.kotest.assertions.assertSoftly
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
            state shouldBe OrderState.CREATED
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
            level = Level.INFO,
            message = "Order created",
            keyValues = mapOf(
                "order.state" to "CREATED",
                "order.total_amount" to 37.0,
                "order.currency" to "EUR",
                "order.item_count" to 5L,
            ),
            mdc = mapOf(
                CorrelationContext.ORDER_ID to order.id,
                CorrelationContext.CUSTOMER_ID to "custom-customer",
            ),
        )
    }
}
