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
import com.observeit.trafficgenerator.traffic.domain.ScenarioExecutionResult
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
        mockFixture.expectPayOrder200(scenario = HAPPY_PATH, orderId = orderId)
        mockFixture.expectShipOrder200(scenario = HAPPY_PATH, orderId = orderId)
        mockFixture.expectGetOrder200(scenario = HAPPY_PATH, orderId = orderId)

        // when
        val result = runner.execute(HAPPY_PATH)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.SHIPPED,
            success = true,
        )
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            Key.CHECKOUT_SCENARIO to "HAPPY_PATH",
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to result.customerId,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order created",
            keyValues = mapOf(Key.ORDER_ID to orderId, Key.ORDER_STATE to State.CREATED),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order reserved",
            keyValues = mapOf(Key.ORDER_STATE to State.RESERVED, Key.ORDER_PREVIOUS_STATE to State.CREATED),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order paid",
            keyValues = mapOf(
                Key.ORDER_STATE to State.PAID,
                Key.ORDER_PREVIOUS_STATE to State.RESERVED,
                Key.PAYMENT_METHOD to Method.CREDIT_CARD,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order shipped",
            keyValues = mapOf(
                Key.ORDER_STATE to State.SHIPPED,
                Key.ORDER_PREVIOUS_STATE to State.PAID,
                "shipping.method" to Method.STANDARD,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to State.SHIPPED,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
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
        mockFixture.expectPayOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        mockFixture.expectShipOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        mockFixture.expectGetOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)

        // when
        val result = runner.execute(LOW_STOCK_WARNING)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = LOW_STOCK_WARNING,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.SHIPPED,
            success = true,
        )
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
        mockFixture.expectGetOrder200(scenario = OUT_OF_STOCK, orderId = orderId, state = State.FAILED)

        // when
        val result = runner.execute(OUT_OF_STOCK)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = OUT_OF_STOCK,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.FAILED,
            success = true,
        )
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            Key.CHECKOUT_SCENARIO to "OUT_OF_STOCK",
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to result.customerId,
        )

        logCapture.assertLogged(
            level = WARN,
            message = "Order reservation failed due to out of stock",
            keyValues = mapOf(
                Key.ERROR_TYPE to "OUT_OF_STOCK",
                "inventory.sku" to "SKU-OUT-OF-STOCK",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to State.FAILED,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
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
            detail = Message.PAYMENT_DECLINED,
        )
        mockFixture.expectGetOrder200(scenario = PAYMENT_DECLINED, orderId = orderId, state = State.FAILED)

        // when
        val result = runner.execute(PAYMENT_DECLINED)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.FAILED,
            success = true,
        )
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            Key.CHECKOUT_SCENARIO to "PAYMENT_DECLINED",
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to result.customerId,
        )

        logCapture.assertLogged(
            level = WARN,
            message = Message.PAYMENT_DECLINED,
            keyValues = mapOf(
                Key.ERROR_TYPE to "PAYMENT_DECLINED",
                Key.PAYMENT_METHOD to Method.DECLINED_CARD,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to State.FAILED,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
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
            detail = Message.PAYMENT_GATEWAY_TIMEOUT,
        )
        mockFixture.expectGetOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId, state = State.FAILED)

        // when
        val result = runner.execute(PAYMENT_GATEWAY_TIMEOUT)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.FAILED,
            success = true,
        )
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            Key.CHECKOUT_SCENARIO to "PAYMENT_GATEWAY_TIMEOUT",
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to result.customerId,
        )

        logCapture.assertLogged(
            level = ERROR,
            message = Message.PAYMENT_GATEWAY_TIMEOUT,
            keyValues = mapOf(
                Key.ERROR_TYPE to "GATEWAY_TIMEOUT",
                Key.PAYMENT_METHOD to Method.TIMEOUT_GATEWAY,
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to State.FAILED,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
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
        mockFixture.expectCancelOrder200(scenario = BUYER_CANCELLATION, orderId = orderId)
        mockFixture.expectGetOrder200(scenario = BUYER_CANCELLATION, orderId = orderId, state = State.CANCELLED)

        // when
        val result = runner.execute(BUYER_CANCELLATION)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.CANCELLED,
            success = true,
        )
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            Key.CHECKOUT_SCENARIO to "BUYER_CANCELLATION",
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to result.customerId,
        )

        logCapture.assertLogged(
            level = INFO,
            message = "Order cancelled",
            keyValues = mapOf(
                Key.ORDER_STATE to State.CANCELLED,
                Key.ORDER_PREVIOUS_STATE to State.RESERVED,
                "cancellation.reason" to "buyer cancellation request",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to State.CANCELLED,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
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
            detail = Message.INVALID_PAY_TRANSITION,
        )
        mockFixture.expectGetOrder200(scenario = INVALID_STATE_TRANSITION, orderId = orderId, state = State.CANCELLED)

        // when
        val result = runner.execute(INVALID_STATE_TRANSITION)

        // then
        result shouldBe ScenarioExecutionResult(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            customerId = result.customerId,
            finalState = State.CANCELLED,
            success = true,
        )
        mockFixture.verify()

        // and
        MDC.getCopyOfContextMap() shouldBe null

        // and
        val expectedMdc = mapOf(
            Key.CHECKOUT_SCENARIO to "INVALID_STATE_TRANSITION",
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to result.customerId,
        )

        logCapture.assertLogged(
            level = WARN,
            message = Message.INVALID_PAY_TRANSITION,
            keyValues = mapOf(
                Key.ERROR_TYPE to "INVALID_STATE_TRANSITION",
                "order.attempted_action" to "pay",
            ),
            mdc = expectedMdc,
        )

        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to State.CANCELLED,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
            ),
            mdc = expectedMdc,
        )
    }

    private companion object {
        object Key {
            const val CHECKOUT_SCENARIO = "checkout.scenario"
            const val ORDER_ID = "order.id"
            const val CUSTOMER_ID = "customer.id"
            const val ORDER_STATE = "order.state"
            const val ORDER_PREVIOUS_STATE = "order.previous_state"
            const val ORDER_TOTAL_AMOUNT = "order.total_amount"
            const val ORDER_CURRENCY = "order.currency"
            const val ORDER_ITEM_COUNT = "order.item_count"
            const val PAYMENT_METHOD = "payment.method"
            const val ERROR_TYPE = "error.type"
        }

        object State {
            const val CREATED = "CREATED"
            const val RESERVED = "RESERVED"
            const val PAID = "PAID"
            const val SHIPPED = "SHIPPED"
            const val CANCELLED = "CANCELLED"
            const val FAILED = "FAILED"
        }

        object Method {
            const val CREDIT_CARD = "CREDIT_CARD"
            const val DECLINED_CARD = "DECLINED_CARD"
            const val TIMEOUT_GATEWAY = "TIMEOUT_GATEWAY"
            const val STANDARD = "STANDARD"
        }

        object Message {
            const val ORDER_RETRIEVED = "Order retrieved"
            const val PAYMENT_DECLINED = "Payment declined for method DECLINED_CARD"
            const val PAYMENT_GATEWAY_TIMEOUT = "Payment gateway timed out"
            const val INVALID_PAY_TRANSITION = "Cannot pay order in CANCELLED state"
        }

        const val CURRENCY_USD = "USD"
        const val DEFAULT_TOTAL_AMOUNT = 10.0
    }
}
