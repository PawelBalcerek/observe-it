package com.observeit.trafficgenerator.traffic

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import com.observeit.trafficgenerator.IntegrationTest
import com.observeit.trafficgenerator.test.LogCapture
import com.observeit.trafficgenerator.test.shouldBeBadRequestError
import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTrafficStatusResponse
import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTriggerScenarioResponse
import com.observeit.trafficgenerator.traffic.api.TrafficApiFixture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.BUYER_CANCELLATION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.INVALID_STATE_TRANSITION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.LOW_STOCK_WARNING
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.client.MockRestServiceServer

@Import(TestObservedAppClientConfiguration::class)
class TrafficTriggerIntegrationTest @Autowired constructor(private val mockServer: MockRestServiceServer) :
    IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture(level = DEBUG)

    private val observedApp = ObservedAppMockFixture(mockServer)
    private val trafficApi by lazy { TrafficApiFixture(restTestClient) }
    private var baselineCounts: Map<CheckoutScenario, Long> = emptyMap()

    @BeforeEach
    fun setUp() {
        mockServer.reset()
        baselineCounts = trafficApi.getStatusAndExpect200().scenarioExecutionCounts
        metricCapture.drainAndReset()
    }

    @AfterEach
    fun tearDown() {
        trafficApi.pauseAndExpect200()
    }

    @Test
    fun `post trigger synchronously executes HAPPY_PATH scenario and emits correlated logs and metrics`() {
        // given
        val orderId = "order-happy"
        observedApp.expectCreateOrder(scenario = HAPPY_PATH, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectPayOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectShipOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectGetOrder200(scenario = HAPPY_PATH, orderId = orderId, state = State.SHIPPED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(HAPPY_PATH)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.SHIPPED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(scenarioExecutionCounts = expectedCountsWithIncrement(HAPPY_PATH))
        assertScenarioMetricsRecorded(scenario = HAPPY_PATH, finalState = State.SHIPPED)

        // and
        val expectedMdc = expectedMdc(scenario = HAPPY_PATH, orderId = orderId, customerId = response.customerId)
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
        assertOrderRetrievedLog(state = State.SHIPPED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger synchronously executes LOW_STOCK_WARNING scenario and emits correlated logs and metrics`() {
        // given
        val orderId = "order-low-stock"
        observedApp.expectCreateOrder(scenario = LOW_STOCK_WARNING, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        observedApp.expectPayOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        observedApp.expectShipOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        observedApp.expectGetOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId, state = State.SHIPPED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(LOW_STOCK_WARNING)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = LOW_STOCK_WARNING,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.SHIPPED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(LOW_STOCK_WARNING),
        )
        assertScenarioMetricsRecorded(scenario = LOW_STOCK_WARNING, finalState = State.SHIPPED)

        // and
        val expectedMdc = expectedMdc(scenario = LOW_STOCK_WARNING, orderId = orderId, customerId = response.customerId)
        assertOrderRetrievedLog(state = State.SHIPPED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger synchronously executes OUT_OF_STOCK scenario and emits warning log and metrics`() {
        // given
        val orderId = "order-out-of-stock"
        observedApp.expectCreateOrder(scenario = OUT_OF_STOCK, returnOrderId = orderId)
        observedApp.expectReserveOrder422(scenario = OUT_OF_STOCK, orderId = orderId)
        observedApp.expectGetOrder200(scenario = OUT_OF_STOCK, orderId = orderId, state = State.FAILED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(OUT_OF_STOCK)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = OUT_OF_STOCK,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.FAILED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(scenarioExecutionCounts = expectedCountsWithIncrement(OUT_OF_STOCK))
        assertScenarioMetricsRecorded(scenario = OUT_OF_STOCK, finalState = State.FAILED)

        // and
        val expectedMdc = expectedMdc(scenario = OUT_OF_STOCK, orderId = orderId, customerId = response.customerId)
        logCapture.assertLogged(
            level = WARN,
            message = "Order reservation failed due to out of stock",
            keyValues = mapOf(
                Key.ERROR_TYPE to "OUT_OF_STOCK",
                "inventory.sku" to "SKU-OUT-OF-STOCK",
            ),
            mdc = expectedMdc,
        )
        assertOrderRetrievedLog(state = State.FAILED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger synchronously executes PAYMENT_DECLINED scenario and emits warning log and metrics`() {
        // given
        val orderId = "order-declined"
        observedApp.expectCreateOrder(scenario = PAYMENT_DECLINED, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = PAYMENT_DECLINED, orderId = orderId)
        observedApp.expectPayOrder422(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            detail = Message.PAYMENT_DECLINED,
        )
        observedApp.expectGetOrder200(scenario = PAYMENT_DECLINED, orderId = orderId, state = State.FAILED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(PAYMENT_DECLINED)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.FAILED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(PAYMENT_DECLINED),
        )
        assertScenarioMetricsRecorded(scenario = PAYMENT_DECLINED, finalState = State.FAILED)

        // and
        val expectedMdc = expectedMdc(scenario = PAYMENT_DECLINED, orderId = orderId, customerId = response.customerId)
        logCapture.assertLogged(
            level = WARN,
            message = Message.PAYMENT_DECLINED,
            keyValues = mapOf(
                Key.ERROR_TYPE to "PAYMENT_DECLINED",
                Key.PAYMENT_METHOD to Method.DECLINED_CARD,
            ),
            mdc = expectedMdc,
        )
        assertOrderRetrievedLog(state = State.FAILED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger synchronously executes PAYMENT_GATEWAY_TIMEOUT scenario and emits error log and metrics`() {
        // given
        val orderId = "order-timeout"
        observedApp.expectCreateOrder(scenario = PAYMENT_GATEWAY_TIMEOUT, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId)
        observedApp.expectPayOrder504(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            detail = Message.PAYMENT_GATEWAY_TIMEOUT,
        )
        observedApp.expectGetOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId, state = State.FAILED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(PAYMENT_GATEWAY_TIMEOUT)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.FAILED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(PAYMENT_GATEWAY_TIMEOUT),
        )
        assertScenarioMetricsRecorded(scenario = PAYMENT_GATEWAY_TIMEOUT, finalState = State.FAILED)

        // and
        val expectedMdc = expectedMdc(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            customerId = response.customerId,
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
        assertOrderRetrievedLog(state = State.FAILED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger synchronously executes BUYER_CANCELLATION scenario and emits cancellation log and metrics`() {
        // given
        val orderId = "order-cancellation"
        observedApp.expectCreateOrder(scenario = BUYER_CANCELLATION, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = BUYER_CANCELLATION, orderId = orderId)
        observedApp.expectCancelOrder200(scenario = BUYER_CANCELLATION, orderId = orderId)
        observedApp.expectGetOrder200(scenario = BUYER_CANCELLATION, orderId = orderId, state = State.CANCELLED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(BUYER_CANCELLATION)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.CANCELLED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(BUYER_CANCELLATION),
        )
        assertScenarioMetricsRecorded(scenario = BUYER_CANCELLATION, finalState = State.CANCELLED)

        // and
        val expectedMdc = expectedMdc(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            customerId = response.customerId,
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
        assertOrderRetrievedLog(state = State.CANCELLED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger synchronously executes INVALID_STATE_TRANSITION scenario and emits warning log and metrics`() {
        // given
        val orderId = "order-invalid-transition"
        observedApp.expectCreateOrder(scenario = INVALID_STATE_TRANSITION, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = INVALID_STATE_TRANSITION, orderId = orderId)
        observedApp.expectCancelOrder200(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            reason = "cancelling before invalid pay",
        )
        observedApp.expectPayOrder409(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            detail = Message.INVALID_PAY_TRANSITION,
        )
        observedApp.expectGetOrder200(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            state = State.CANCELLED,
        )

        // when
        val response = trafficApi.triggerScenarioAndExpect200(INVALID_STATE_TRANSITION)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.CANCELLED,
            success = true,
        )
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(INVALID_STATE_TRANSITION),
        )
        assertScenarioMetricsRecorded(scenario = INVALID_STATE_TRANSITION, finalState = State.CANCELLED)

        // and
        val expectedMdc = expectedMdc(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            customerId = response.customerId,
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
        assertOrderRetrievedLog(state = State.CANCELLED, mdc = expectedMdc)
    }

    @Test
    fun `post trigger executes on demand when background workers are explicitly paused`() {
        // given
        trafficApi.pauseAndExpect200()
        val orderId = "order-while-paused"
        observedApp.expectCreateOrder(scenario = HAPPY_PATH, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectPayOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectShipOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectGetOrder200(scenario = HAPPY_PATH, orderId = orderId, state = State.SHIPPED)

        // when
        val response = trafficApi.triggerScenarioAndExpect200(HAPPY_PATH)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
            finalState = State.SHIPPED,
            success = true,
        )

        // and
        observedApp.verify()
        assertScenarioMetricsRecorded(scenario = HAPPY_PATH, finalState = State.SHIPPED)
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("invalidScenarios")
    fun `post trigger returns 400 Bad Request for unrecognized scenario name`(
        scenarioName: String,
        expectedDetail: String,
    ) {
        // when
        val problem = trafficApi.triggerScenarioAndExpect400(scenarioName)

        // then
        problem.shouldBeBadRequestError(expectedDetail = expectedDetail)
    }

    private fun expectedCountsWithIncrement(incrementedScenario: CheckoutScenario): Map<CheckoutScenario, Long> =
        baselineCounts + (incrementedScenario to baselineCounts.getValue(incrementedScenario) + 1L)

    private fun expectedMdc(scenario: CheckoutScenario, orderId: String, customerId: String): Map<String, String> =
        mapOf(
            Key.CHECKOUT_SCENARIO to scenario.name,
            Key.ORDER_ID to orderId,
            Key.CUSTOMER_ID to customerId,
        )

    private fun assertOrderRetrievedLog(state: String, mdc: Map<String, String>) {
        logCapture.assertLogged(
            level = INFO,
            message = Message.ORDER_RETRIEVED,
            keyValues = mapOf(
                Key.ORDER_STATE to state,
                Key.ORDER_TOTAL_AMOUNT to DEFAULT_TOTAL_AMOUNT,
                Key.ORDER_CURRENCY to CURRENCY_USD,
                Key.ORDER_ITEM_COUNT to 1L,
            ),
            mdc = mdc,
        )
    }

    private fun assertScenarioMetricsRecorded(scenario: CheckoutScenario, finalState: String) {
        metricCapture.assertCounterIncremented(
            name = "traffic.scenarios.executed",
            unit = "{scenario}",
            delta = 1L,
            attributes = mapOf(
                Key.CHECKOUT_SCENARIO to scenario.name,
                Key.ORDER_STATE to finalState,
            ),
        )
        val durationPoint = metricCapture.assertHistogramRecorded(
            name = "traffic.scenario.duration",
            unit = "ms",
            count = 1L,
            attributes = mapOf(Key.CHECKOUT_SCENARIO to scenario.name),
        )
        durationPoint.sum shouldBeGreaterThanOrEqualTo 0.0
    }

    companion object {
        private object Key {
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

        private object State {
            const val CREATED = "CREATED"
            const val RESERVED = "RESERVED"
            const val PAID = "PAID"
            const val SHIPPED = "SHIPPED"
            const val CANCELLED = "CANCELLED"
            const val FAILED = "FAILED"
        }

        private object Method {
            const val CREDIT_CARD = "CREDIT_CARD"
            const val DECLINED_CARD = "DECLINED_CARD"
            const val TIMEOUT_GATEWAY = "TIMEOUT_GATEWAY"
            const val STANDARD = "STANDARD"
        }

        private object Message {
            const val ORDER_RETRIEVED = "Order retrieved"
            const val PAYMENT_DECLINED = "Payment declined for method DECLINED_CARD"
            const val PAYMENT_GATEWAY_TIMEOUT = "Payment gateway timed out"
            const val INVALID_PAY_TRANSITION = "Cannot pay order in CANCELLED state"
        }

        private const val CURRENCY_USD = "USD"
        private const val DEFAULT_TOTAL_AMOUNT = 10.0

        @JvmStatic
        val invalidScenarios = listOf(
            Arguments.of(
                "UNKNOWN_SCENARIO",
                "Failed to convert parameter 'scenario' with value 'UNKNOWN_SCENARIO'",
            ),
            Arguments.of(
                "happy_path",
                "Failed to convert parameter 'scenario' with value 'happy_path'",
            ),
            Arguments.of(
                "invalid",
                "Failed to convert parameter 'scenario' with value 'invalid'",
            ),
            Arguments.of(
                "123",
                "Failed to convert parameter 'scenario' with value '123'",
            ),
        )
    }
}
