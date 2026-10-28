package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.correlation.CorrelationContext.CHECKOUT_SCENARIO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.api.OrderLineItemResponse
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeNotFoundError
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal

class GetOrderIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `get orders by id returns 200 OK with order details and emits structured log with MDC correlation`() {
        // given
        val created = orderApi.createOrderAndExpect201(aCreateOrderRequest())

        // when
        val response = orderApi.getOrderAndExpect200(created.orderId, checkoutScenario = "happy-path")

        // then
        assertSoftly(response) {
            orderId shouldBe created.orderId
            customerId shouldBe "customerId"
            state shouldBe CREATED
            totalAmount shouldBe BigDecimal("10.00")
            currency shouldBe "USD"
            items shouldBe listOf(
                OrderLineItemResponse(
                    sku = "sku",
                    quantity = 1,
                    unitPrice = BigDecimal("10.00"),
                    currency = "USD",
                ),
            )
        }

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
                CHECKOUT_SCENARIO to "happy-path",
                ORDER_ID to created.orderId,
                CUSTOMER_ID to "customerId",
            ),
        )
    }

    @Test
    fun `get orders by id returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.getOrderAndExpect404("non-existent-order", checkoutScenario = "lookup-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertLogged(
            level = WARN,
            message = "Order not found: non-existent-order",
            mdc = mapOf(
                CHECKOUT_SCENARIO to "lookup-miss",
                ORDER_ID to "non-existent-order",
            ),
        )
    }
}
