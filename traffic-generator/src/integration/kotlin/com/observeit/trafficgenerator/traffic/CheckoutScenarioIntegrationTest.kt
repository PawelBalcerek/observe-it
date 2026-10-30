package com.observeit.trafficgenerator.traffic

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.trafficgenerator.IntegrationTest
import com.observeit.trafficgenerator.test.LogCapture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.BUYER_CANCELLATION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.INVALID_STATE_TRANSITION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.LOW_STOCK_WARNING
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenarioRunner
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

@Import(CheckoutScenarioIntegrationTest.MockServerConfig::class)
class CheckoutScenarioIntegrationTest @Autowired constructor(
    private val mockServer: MockRestServiceServer,
    private val runner: CheckoutScenarioRunner,
) : IntegrationTest() {

    @TestConfiguration
    class MockServerConfig {
        private val builder = RestClient.builder().baseUrl("http://localhost:8080")
        private val mockServer = MockRestServiceServer.bindTo(builder).build()
        private val client = builder.build()

        @Bean
        fun mockServer(): MockRestServiceServer = mockServer

        @Bean
        @Primary
        fun testObservedAppRestClient(): RestClient = client
    }

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture(level = DEBUG)

    private lateinit var mockFixture: ObservedAppMockFixture

    @BeforeEach
    fun setUp() {
        mockServer.reset()
        mockFixture = ObservedAppMockFixture(mockServer)
    }

    @Test
    fun `executes HAPPY_PATH checkout scenario and emits correlated logs`() {
        // given
        val orderId = "order-happy"
        mockFixture.expectCreateOrder(scenario = HAPPY_PATH, returnOrderId = orderId)
        mockFixture.expectReserveOrder200(scenario = HAPPY_PATH, orderId = orderId)
        mockFixture.expectPayOrder200(scenario = HAPPY_PATH, orderId = orderId, paymentMethod = "CREDIT_CARD")
        mockFixture.expectShipOrder200(scenario = HAPPY_PATH, orderId = orderId, shippingMethod = "STANDARD")
        mockFixture.expectGetOrder200(scenario = HAPPY_PATH, orderId = orderId, state = "SHIPPED")

        // when
        val result = runner.execute(HAPPY_PATH)

        // then
        result.scenario shouldBe HAPPY_PATH
        result.orderId shouldBe orderId
        result.finalState shouldBe "SHIPPED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            "checkout.scenario" to "HAPPY_PATH",
            "order.id" to orderId,
            "customer.id" to result.customerId,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order created",
            keyValues = mapOf("order.id" to orderId, "order.state" to "CREATED"),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order reserved",
            keyValues = mapOf("order.state" to "RESERVED", "order.previous_state" to "CREATED"),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order paid",
            keyValues = mapOf(
                "order.state" to "PAID",
                "order.previous_state" to "RESERVED",
                "payment.method" to "CREDIT_CARD",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order shipped",
            keyValues = mapOf(
                "order.state" to "SHIPPED",
                "order.previous_state" to "PAID",
                "shipping.method" to "STANDARD",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "SHIPPED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    @Test
    fun `executes LOW_STOCK_WARNING checkout scenario to SHIPPED state`() {
        // given
        val orderId = "order-low-stock"
        mockFixture.expectCreateOrder(scenario = LOW_STOCK_WARNING, returnOrderId = orderId)
        mockFixture.expectReserveOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        mockFixture.expectPayOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId, paymentMethod = "CREDIT_CARD")
        mockFixture.expectShipOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId, shippingMethod = "STANDARD")
        mockFixture.expectGetOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId, state = "SHIPPED")

        // when
        val result = runner.execute(LOW_STOCK_WARNING)

        // then
        result.scenario shouldBe LOW_STOCK_WARNING
        result.orderId shouldBe orderId
        result.finalState shouldBe "SHIPPED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null
    }

    @Test
    fun `executes OUT_OF_STOCK checkout scenario with 422 handling to FAILED state and emits warning log`() {
        // given
        val orderId = "order-out-of-stock"
        mockFixture.expectCreateOrder(scenario = OUT_OF_STOCK, returnOrderId = orderId)
        mockFixture.expectReserveOrder422(
            scenario = OUT_OF_STOCK,
            orderId = orderId,
            detail = "Inventory out of stock for SKU-OUT-OF-STOCK",
        )
        mockFixture.expectGetOrder200(scenario = OUT_OF_STOCK, orderId = orderId, state = "FAILED")

        // when
        val result = runner.execute(OUT_OF_STOCK)

        // then
        result.scenario shouldBe OUT_OF_STOCK
        result.orderId shouldBe orderId
        result.finalState shouldBe "FAILED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            "checkout.scenario" to "OUT_OF_STOCK",
            "order.id" to orderId,
            "customer.id" to result.customerId,
        )

        logCapture.assertLogged(
            level = WARN,
            message = "Order reservation failed due to out of stock",
            keyValues = mapOf(
                "error.type" to "OUT_OF_STOCK",
                "inventory.sku" to "SKU-OUT-OF-STOCK",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "FAILED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    @Test
    fun `executes PAYMENT_DECLINED checkout scenario with 422 handling to FAILED state and emits warning log`() {
        // given
        val orderId = "order-declined"
        mockFixture.expectCreateOrder(scenario = PAYMENT_DECLINED, returnOrderId = orderId)
        mockFixture.expectReserveOrder200(scenario = PAYMENT_DECLINED, orderId = orderId)
        mockFixture.expectPayOrder422(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            paymentMethod = "DECLINED_CARD",
            detail = "Payment declined for method DECLINED_CARD",
        )
        mockFixture.expectGetOrder200(scenario = PAYMENT_DECLINED, orderId = orderId, state = "FAILED")

        // when
        val result = runner.execute(PAYMENT_DECLINED)

        // then
        result.scenario shouldBe PAYMENT_DECLINED
        result.orderId shouldBe orderId
        result.finalState shouldBe "FAILED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            "checkout.scenario" to "PAYMENT_DECLINED",
            "order.id" to orderId,
            "customer.id" to result.customerId,
        )

        logCapture.assertLogged(
            level = WARN,
            message = "Payment declined for method DECLINED_CARD",
            keyValues = mapOf(
                "error.type" to "PAYMENT_DECLINED",
                "payment.method" to "DECLINED_CARD",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "FAILED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    @Test
    fun `executes PAYMENT_GATEWAY_TIMEOUT checkout scenario with 504 handling to FAILED state and emits error log`() {
        // given
        val orderId = "order-timeout"
        mockFixture.expectCreateOrder(scenario = PAYMENT_GATEWAY_TIMEOUT, returnOrderId = orderId)
        mockFixture.expectReserveOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId)
        mockFixture.expectPayOrder504(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            paymentMethod = "TIMEOUT_GATEWAY",
            detail = "Payment gateway timed out",
        )
        mockFixture.expectGetOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId, state = "FAILED")

        // when
        val result = runner.execute(PAYMENT_GATEWAY_TIMEOUT)

        // then
        result.scenario shouldBe PAYMENT_GATEWAY_TIMEOUT
        result.orderId shouldBe orderId
        result.finalState shouldBe "FAILED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            "checkout.scenario" to "PAYMENT_GATEWAY_TIMEOUT",
            "order.id" to orderId,
            "customer.id" to result.customerId,
        )

        logCapture.assertLogged(
            level = ERROR,
            message = "Payment gateway timed out",
            keyValues = mapOf(
                "error.type" to "GATEWAY_TIMEOUT",
                "payment.method" to "TIMEOUT_GATEWAY",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "FAILED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    @Test
    fun `executes BUYER_CANCELLATION checkout scenario to CANCELLED state and emits cancellation log`() {
        // given
        val orderId = "order-cancellation"
        mockFixture.expectCreateOrder(scenario = BUYER_CANCELLATION, returnOrderId = orderId)
        mockFixture.expectReserveOrder200(scenario = BUYER_CANCELLATION, orderId = orderId)
        mockFixture.expectCancelOrder200(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            reason = "buyer cancellation request",
        )
        mockFixture.expectGetOrder200(scenario = BUYER_CANCELLATION, orderId = orderId, state = "CANCELLED")

        // when
        val result = runner.execute(BUYER_CANCELLATION)

        // then
        result.scenario shouldBe BUYER_CANCELLATION
        result.orderId shouldBe orderId
        result.finalState shouldBe "CANCELLED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            "checkout.scenario" to "BUYER_CANCELLATION",
            "order.id" to orderId,
            "customer.id" to result.customerId,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.previous_state" to "RESERVED",
                "cancellation.reason" to "buyer cancellation request",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    @Test
    fun `executes INVALID_STATE_TRANSITION checkout scenario to CANCELLED state and emits warning log`() {
        // given
        val orderId = "order-invalid-transition"
        mockFixture.expectCreateOrder(scenario = INVALID_STATE_TRANSITION, returnOrderId = orderId)
        mockFixture.expectReserveOrder200(scenario = INVALID_STATE_TRANSITION, orderId = orderId)
        mockFixture.expectCancelOrder200(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            reason = "cancelling before invalid pay",
        )
        mockFixture.expectPayOrder409(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            detail = "Cannot pay order in CANCELLED state",
        )
        mockFixture.expectGetOrder200(scenario = INVALID_STATE_TRANSITION, orderId = orderId, state = "CANCELLED")

        // when
        val result = runner.execute(INVALID_STATE_TRANSITION)

        // then
        result.scenario shouldBe INVALID_STATE_TRANSITION
        result.orderId shouldBe orderId
        result.finalState shouldBe "CANCELLED"
        result.success shouldBe true
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            "checkout.scenario" to "INVALID_STATE_TRANSITION",
            "order.id" to orderId,
            "customer.id" to result.customerId,
        )

        logCapture.assertLogged(
            level = WARN,
            message = "Cannot pay order in CANCELLED state",
            keyValues = mapOf(
                "error.type" to "INVALID_STATE_TRANSITION",
                "order.attempted_action" to "pay",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order retrieved",
            keyValues = mapOf(
                "order.state" to "CANCELLED",
                "order.total_amount" to 10.0,
                "order.currency" to "USD",
                "order.item_count" to 1L,
            ),
            mdc = expectedMdc,
        )
    }
}
