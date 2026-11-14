package com.observeit.observedapp.order

import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.OrderFixtures.anOrderResponse
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.assertOrderNotFoundLog
import com.observeit.observedapp.order.assertOrderRetrievedLog
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
        logCapture.assertOrderRetrievedLog(
            orderId = created.orderId,
            checkoutScenario = "happy-path",
        )
    }

    @Test
    fun `get orders by id returns 404 Not Found when order does not exist and logs warning with MDC correlation`() {
        // when
        val problem = orderApi.getOrderAndExpect404("non-existent-order", checkoutScenario = "lookup-miss")

        // then
        problem.shouldBeNotFoundError("Order not found: non-existent-order")

        // and
        logCapture.assertOrderNotFoundLog(
            orderId = "non-existent-order",
            checkoutScenario = "lookup-miss",
        )
    }
}
