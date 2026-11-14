package com.observeit.trafficgenerator.traffic

import ch.qos.logback.classic.Level.DEBUG
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
        metricCapture.assertScenarioExecutedIncremented(scenario = HAPPY_PATH, finalState = State.SHIPPED)
        metricCapture.assertScenarioDurationRecorded(scenario = HAPPY_PATH)

        // and
        logCapture.assertOrderCreatedLog(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderReservedLog(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderPaidLog(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderShippedLog(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderRetrievedLog(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
            state = State.SHIPPED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(scenario = LOW_STOCK_WARNING, finalState = State.SHIPPED)
        metricCapture.assertScenarioDurationRecorded(scenario = LOW_STOCK_WARNING)

        // and
        logCapture.assertOrderRetrievedLog(
            scenario = LOW_STOCK_WARNING,
            orderId = orderId,
            customerId = response.customerId,
            state = State.SHIPPED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(scenario = OUT_OF_STOCK, finalState = State.FAILED)
        metricCapture.assertScenarioDurationRecorded(scenario = OUT_OF_STOCK)

        // and
        logCapture.assertOutOfStockLog(
            scenario = OUT_OF_STOCK,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderRetrievedLog(
            scenario = OUT_OF_STOCK,
            orderId = orderId,
            customerId = response.customerId,
            state = State.FAILED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(scenario = PAYMENT_DECLINED, finalState = State.FAILED)
        metricCapture.assertScenarioDurationRecorded(scenario = PAYMENT_DECLINED)

        // and
        logCapture.assertPaymentDeclinedLog(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderRetrievedLog(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            customerId = response.customerId,
            state = State.FAILED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            finalState = State.FAILED,
        )
        metricCapture.assertScenarioDurationRecorded(scenario = PAYMENT_GATEWAY_TIMEOUT)

        // and
        logCapture.assertPaymentGatewayTimeoutLog(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderRetrievedLog(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            customerId = response.customerId,
            state = State.FAILED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(scenario = BUYER_CANCELLATION, finalState = State.CANCELLED)
        metricCapture.assertScenarioDurationRecorded(scenario = BUYER_CANCELLATION)

        // and
        logCapture.assertOrderCancelledLog(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderRetrievedLog(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            customerId = response.customerId,
            state = State.CANCELLED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(
            scenario = INVALID_STATE_TRANSITION,
            finalState = State.CANCELLED,
        )
        metricCapture.assertScenarioDurationRecorded(scenario = INVALID_STATE_TRANSITION)

        // and
        logCapture.assertInvalidStateTransitionLog(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            customerId = response.customerId,
        )
        logCapture.assertOrderRetrievedLog(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            customerId = response.customerId,
            state = State.CANCELLED,
        )
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
        metricCapture.assertScenarioExecutedIncremented(scenario = HAPPY_PATH, finalState = State.SHIPPED)
        metricCapture.assertScenarioDurationRecorded(scenario = HAPPY_PATH)
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

    companion object {
        private object State {
            const val SHIPPED = "SHIPPED"
            const val CANCELLED = "CANCELLED"
            const val FAILED = "FAILED"
        }

        private object Message {
            const val PAYMENT_DECLINED = "Payment declined for method DECLINED_CARD"
            const val PAYMENT_GATEWAY_TIMEOUT = "Payment gateway timed out"
            const val INVALID_PAY_TRANSITION = "Cannot pay order in CANCELLED state"
        }

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
