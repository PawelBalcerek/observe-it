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
            returnOrderId = Default.ORDER_ID,
        )

        // when
        val result = client.createOrder(
            scenario = HAPPY_PATH,
            customerId = Default.CUSTOMER_ID,
            items = listOf(
                CreateOrderItem(
                    sku = Default.SKU,
                    quantity = 2,
                    unitPrice = Default.UNIT_PRICE,
                    currency = Default.CURRENCY,
                ),
            ),
        )

        // then
        result shouldBe CreateOrderResult(
            orderId = Default.ORDER_ID,
            customerId = Default.CUSTOMER_ID,
            state = State.CREATED,
        )
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
                customerId = Default.CUSTOMER_ID,
                items = listOf(
                    CreateOrderItem(
                        sku = Default.SKU,
                        quantity = 2,
                        unitPrice = Default.UNIT_PRICE,
                        currency = Default.CURRENCY,
                    ),
                ),
            )
        }
    }

    // --- reserveOrder tests ---

    @Test
    fun `reserves order with 200 OK`() {
        // given
        mockFixture.expectReserveOrder200(scenario = LOW_STOCK_WARNING, orderId = Default.ORDER_ID)

        // when
        val result = client.reserveOrder(scenario = LOW_STOCK_WARNING, orderId = Default.ORDER_ID)

        // then
        result shouldBe ReserveOrderResult.Success(orderId = Default.ORDER_ID, state = "RESERVED")
        mockFixture.verify()
    }

    @Test
    fun `reserves order with 422 UNPROCESSABLE_CONTENT on out of stock`() {
        // given
        mockFixture.expectReserveOrder422(scenario = OUT_OF_STOCK, orderId = Default.ORDER_ID)

        // when
        val result = client.reserveOrder(scenario = OUT_OF_STOCK, orderId = Default.ORDER_ID)

        // then
        result shouldBe ReserveOrderResult.OutOfStock(detail = "Requested quantity exceeds available stock")
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when reserveOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectReserveOrder500(orderId = Default.ORDER_ID)

        // when / then
        shouldThrow<RestClientResponseException> {
            client.reserveOrder(scenario = HAPPY_PATH, orderId = Default.ORDER_ID)
        }
    }

    // --- payOrder tests ---

    @Test
    fun `pays order with 200 OK`() {
        // given
        mockFixture.expectPayOrder200(
            scenario = HAPPY_PATH,
            orderId = Default.ORDER_ID,
            paymentMethod = Method.CREDIT_CARD,
        )

        // when
        val result = client.payOrder(
            scenario = HAPPY_PATH,
            orderId = Default.ORDER_ID,
            paymentMethod = Method.CREDIT_CARD,
        )

        // then
        result shouldBe PayOrderResult.Success(orderId = Default.ORDER_ID, state = "PAID")
        mockFixture.verify()
    }

    @Test
    fun `pays order with 409 CONFLICT on invalid state transition`() {
        // given
        mockFixture.expectPayOrder409(scenario = INVALID_STATE_TRANSITION, orderId = Default.ORDER_ID)

        // when
        val result = client.payOrder(
            scenario = INVALID_STATE_TRANSITION,
            orderId = Default.ORDER_ID,
            paymentMethod = Method.CREDIT_CARD,
        )

        // then
        result shouldBe PayOrderResult.InvalidTransition(detail = "Cannot pay order in CANCELLED state")
        mockFixture.verify()
    }

    @Test
    fun `pays order with 422 UNPROCESSABLE_CONTENT on declined card`() {
        // given
        mockFixture.expectPayOrder422(scenario = PAYMENT_DECLINED, orderId = Default.ORDER_ID)

        // when
        val result = client.payOrder(
            scenario = PAYMENT_DECLINED,
            orderId = Default.ORDER_ID,
            paymentMethod = "DECLINED_CARD",
        )

        // then
        result shouldBe PayOrderResult.Declined(detail = "Payment was declined by card issuer")
        mockFixture.verify()
    }

    @Test
    fun `pays order with 504 GATEWAY_TIMEOUT on payment gateway timeout`() {
        // given
        mockFixture.expectPayOrder504(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = Default.ORDER_ID)

        // when
        val result = client.payOrder(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = Default.ORDER_ID,
            paymentMethod = "TIMEOUT_GATEWAY",
        )

        // then
        result shouldBe PayOrderResult.Timeout(detail = "Payment gateway timed out after 3000ms")
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when payOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectPayOrder500(orderId = Default.ORDER_ID)

        // when / then
        shouldThrow<RestClientResponseException> {
            client.payOrder(scenario = HAPPY_PATH, orderId = Default.ORDER_ID, paymentMethod = Method.CREDIT_CARD)
        }
    }

    // --- shipOrder tests ---

    @Test
    fun `ships order with 200 OK`() {
        // given
        mockFixture.expectShipOrder200(
            scenario = HAPPY_PATH,
            orderId = Default.ORDER_ID,
            shippingMethod = Method.STANDARD,
        )

        // when
        val result = client.shipOrder(
            scenario = HAPPY_PATH,
            orderId = Default.ORDER_ID,
            shippingMethod = Method.STANDARD,
        )

        // then
        result shouldBe ShipOrderResult.Success(
            orderId = Default.ORDER_ID,
            state = State.SHIPPED,
            shippingMethod = Method.STANDARD,
        )
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when shipOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectShipOrder500(orderId = Default.ORDER_ID)

        // when / then
        shouldThrow<RestClientResponseException> {
            client.shipOrder(scenario = HAPPY_PATH, orderId = Default.ORDER_ID, shippingMethod = Method.STANDARD)
        }
    }

    // --- cancelOrder tests ---

    @Test
    fun `cancels order with 200 OK`() {
        // given
        mockFixture.expectCancelOrder200(
            scenario = BUYER_CANCELLATION,
            orderId = Default.ORDER_ID,
            reason = Default.CANCELLATION_REASON,
        )

        // when
        val result = client.cancelOrder(
            scenario = BUYER_CANCELLATION,
            orderId = Default.ORDER_ID,
            reason = Default.CANCELLATION_REASON,
        )

        // then
        result shouldBe CancelOrderResult.Success(
            orderId = Default.ORDER_ID,
            state = "CANCELLED",
            reason = Default.CANCELLATION_REASON,
        )
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when cancelOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectCancelOrder500(orderId = Default.ORDER_ID)

        // when / then
        shouldThrow<RestClientResponseException> {
            client.cancelOrder(scenario = BUYER_CANCELLATION, orderId = Default.ORDER_ID, reason = "cancel")
        }
    }

    // --- getOrder tests ---

    @Test
    fun `gets order details with 200 OK`() {
        // given
        mockFixture.expectGetOrder200(
            scenario = HAPPY_PATH,
            orderId = Default.ORDER_ID,
            state = State.SHIPPED,
            customerId = Default.CUSTOMER_ID,
        )

        // when
        val result = client.getOrder(scenario = HAPPY_PATH, orderId = Default.ORDER_ID)

        // then
        result shouldBe OrderDetails(
            orderId = Default.ORDER_ID,
            customerId = Default.CUSTOMER_ID,
            state = State.SHIPPED,
            totalAmount = 10.00,
            currency = Default.CURRENCY,
            itemCount = 1,
        )
        mockFixture.verify()
    }

    @Test
    fun `throws RestClientResponseException when getOrder receives 500 INTERNAL_SERVER_ERROR`() {
        // given
        mockFixture.expectGetOrder500(orderId = Default.ORDER_ID)

        // when / then
        shouldThrow<RestClientResponseException> {
            client.getOrder(scenario = HAPPY_PATH, orderId = Default.ORDER_ID)
        }
    }

    private companion object {
        object Default {
            const val ORDER_ID = "order-1"
            const val CUSTOMER_ID = "customer-1"
            const val SKU = "SKU-1"
            const val CURRENCY = "USD"
            val UNIT_PRICE: BigDecimal = BigDecimal("10.00")
            const val CANCELLATION_REASON = "buyer cancellation request"
        }

        object State {
            const val CREATED = "CREATED"
            const val SHIPPED = "SHIPPED"
        }

        object Method {
            const val CREDIT_CARD = "CREDIT_CARD"
            const val STANDARD = "STANDARD"
        }
    }
}
