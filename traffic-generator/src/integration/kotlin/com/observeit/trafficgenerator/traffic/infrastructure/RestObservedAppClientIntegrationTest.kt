package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.ObservedAppMockFixture
import com.observeit.trafficgenerator.traffic.domain.CancelOrderResult
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.BUYER_CANCELLATION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.INVALID_STATE_TRANSITION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.LOW_STOCK_WARNING
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import com.observeit.trafficgenerator.traffic.domain.CreateOrderItem
import com.observeit.trafficgenerator.traffic.domain.CreateOrderResult
import com.observeit.trafficgenerator.traffic.domain.OrderDetails
import com.observeit.trafficgenerator.traffic.domain.PayOrderResult
import com.observeit.trafficgenerator.traffic.domain.ReserveOrderResult
import com.observeit.trafficgenerator.traffic.domain.ShipOrderResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal

class RestObservedAppClientIntegrationTest {

    private val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8080")
    private val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val client = RestObservedAppClient(restClientBuilder.build())
    private val mockFixture = ObservedAppMockFixture(mockServer)

    @BeforeEach
    fun setUp() {
        mockServer.reset()
    }

    // --- createOrder tests ---

    @Test
    fun `creates order with 201 CREATED and propagates X-Checkout-Scenario header`() {
        // given
        mockFixture.expectCreateOrder(
            scenario = HAPPY_PATH,
            expectedCustomerId = "customer-1",
            returnOrderId = "order-1",
            returnState = "CREATED",
        )

        // when
        val result = client.createOrder(
            scenario = HAPPY_PATH,
            customerId = "customer-1",
            items = listOf(
                CreateOrderItem(
                    sku = "SKU-1",
                    quantity = 2,
                    unitPrice = BigDecimal("10.00"),
                    currency = "USD",
                ),
            ),
        )

        // then
        result shouldBe CreateOrderResult(orderId = "order-1", customerId = "customer-1", state = "CREATED")
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when createOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectCreateOrder500()

        // when / then
        shouldThrow<RestClientResponseException> {
            client.createOrder(
                scenario = HAPPY_PATH,
                customerId = "customer-1",
                items = listOf(
                    CreateOrderItem(
                        sku = "SKU-1",
                        quantity = 2,
                        unitPrice = BigDecimal("10.00"),
                        currency = "USD",
                    ),
                ),
            )
        }
    }

    // --- reserveOrder tests ---

    @Test
    fun `reserves order with 200 OK`() {
        // given
        mockFixture.expectReserveOrder200(scenario = LOW_STOCK_WARNING, orderId = "order-1")

        // when
        val result = client.reserveOrder(scenario = LOW_STOCK_WARNING, orderId = "order-1")

        // then
        result shouldBe ReserveOrderResult.Success(orderId = "order-1", state = "RESERVED")
        mockFixture.verify()
    }

    @Test
    fun `reserves order with 422 UNPROCESSABLE_CONTENT on out of stock`() {
        // given
        mockFixture.expectReserveOrder422OutOfStock(scenario = OUT_OF_STOCK, orderId = "order-1")

        // when
        val result = client.reserveOrder(scenario = OUT_OF_STOCK, orderId = "order-1")

        // then
        result shouldBe ReserveOrderResult.OutOfStock(detail = "Requested quantity exceeds available stock")
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when reserveOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectReserveOrder500(orderId = "order-1")

        // when / then
        shouldThrow<RestClientResponseException> {
            client.reserveOrder(scenario = HAPPY_PATH, orderId = "order-1")
        }
    }

    // --- payOrder tests ---

    @Test
    fun `pays order with 200 OK`() {
        // given
        mockFixture.expectPayOrder200(scenario = HAPPY_PATH, orderId = "order-1", paymentMethod = "CREDIT_CARD")

        // when
        val result = client.payOrder(scenario = HAPPY_PATH, orderId = "order-1", paymentMethod = "CREDIT_CARD")

        // then
        result shouldBe PayOrderResult.Success(orderId = "order-1", state = "PAID")
        mockFixture.verify()
    }

    @Test
    fun `pays order with 409 CONFLICT on invalid state transition`() {
        // given
        mockFixture.expectPayOrder409Conflict(scenario = INVALID_STATE_TRANSITION, orderId = "order-1")

        // when
        val result = client.payOrder(
            scenario = INVALID_STATE_TRANSITION,
            orderId = "order-1",
            paymentMethod = "CREDIT_CARD",
        )

        // then
        result shouldBe PayOrderResult.InvalidTransition(detail = "Cannot pay order in CANCELLED state")
        mockFixture.verify()
    }

    @Test
    fun `pays order with 422 UNPROCESSABLE_CONTENT on declined card`() {
        // given
        mockFixture.expectPayOrder422Declined(scenario = PAYMENT_DECLINED, orderId = "order-1")

        // when
        val result = client.payOrder(scenario = PAYMENT_DECLINED, orderId = "order-1", paymentMethod = "DECLINED_CARD")

        // then
        result shouldBe PayOrderResult.Declined(detail = "Payment was declined by card issuer")
        mockFixture.verify()
    }

    @Test
    fun `pays order with 504 GATEWAY_TIMEOUT on payment gateway timeout`() {
        // given
        mockFixture.expectPayOrder504Timeout(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = "order-1")

        // when
        val result = client.payOrder(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = "order-1",
            paymentMethod = "TIMEOUT_GATEWAY",
        )

        // then
        result shouldBe PayOrderResult.Timeout(detail = "Payment gateway timed out after 3000ms")
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when payOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectPayOrder500(orderId = "order-1")

        // when / then
        shouldThrow<RestClientResponseException> {
            client.payOrder(scenario = HAPPY_PATH, orderId = "order-1", paymentMethod = "CREDIT_CARD")
        }
    }

    // --- shipOrder tests ---

    @Test
    fun `ships order with 200 OK`() {
        // given
        mockFixture.expectShipOrder200(scenario = HAPPY_PATH, orderId = "order-1", shippingMethod = "STANDARD")

        // when
        val result = client.shipOrder(scenario = HAPPY_PATH, orderId = "order-1", shippingMethod = "STANDARD")

        // then
        result shouldBe ShipOrderResult.Success(orderId = "order-1", state = "SHIPPED", shippingMethod = "STANDARD")
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when shipOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectShipOrder500(orderId = "order-1")

        // when / then
        shouldThrow<RestClientResponseException> {
            client.shipOrder(scenario = HAPPY_PATH, orderId = "order-1", shippingMethod = "STANDARD")
        }
    }

    // --- cancelOrder tests ---

    @Test
    fun `cancels order with 200 OK`() {
        // given
        mockFixture.expectCancelOrder200(
            scenario = BUYER_CANCELLATION,
            orderId = "order-1",
            reason = "buyer cancellation request",
        )

        // when
        val result = client.cancelOrder(
            scenario = BUYER_CANCELLATION,
            orderId = "order-1",
            reason = "buyer cancellation request",
        )

        // then
        result shouldBe CancelOrderResult.Success(
            orderId = "order-1",
            state = "CANCELLED",
            reason = "buyer cancellation request",
        )
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when cancelOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectCancelOrder500(orderId = "order-1")

        // when / then
        shouldThrow<RestClientResponseException> {
            client.cancelOrder(scenario = BUYER_CANCELLATION, orderId = "order-1", reason = "cancel")
        }
    }

    // --- getOrder tests ---

    @Test
    fun `gets order details with 200 OK`() {
        // given
        mockFixture.expectGetOrder200(
            scenario = HAPPY_PATH,
            orderId = "order-1",
            state = "SHIPPED",
            customerId = "cust-1",
        )

        // when
        val result = client.getOrder(scenario = HAPPY_PATH, orderId = "order-1")

        // then
        result shouldBe OrderDetails(
            orderId = "order-1",
            customerId = "cust-1",
            state = "SHIPPED",
            totalAmount = 10.00,
            currency = "USD",
            itemCount = 1,
        )
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when getOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectGetOrder500(orderId = "order-1")

        // when / then
        shouldThrow<RestClientResponseException> {
            client.getOrder(scenario = HAPPY_PATH, orderId = "order-1")
        }
    }
}
