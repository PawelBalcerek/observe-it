package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.anOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeNotFoundError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

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
        response shouldBe anOrderResponse(orderId = created.orderId)

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
                "checkout.scenario" to "happy-path",
                "order.id" to created.orderId,
                "customer.id" to "customerId",
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
                "checkout.scenario" to "lookup-miss",
                "order.id" to "non-existent-order",
            ),
        )
    }
}
