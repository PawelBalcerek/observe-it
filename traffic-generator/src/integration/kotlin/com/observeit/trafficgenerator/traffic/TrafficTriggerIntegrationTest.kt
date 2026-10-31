package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.IntegrationTest
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@Import(TrafficTriggerIntegrationTest.MockServerConfig::class)
class TrafficTriggerIntegrationTest @Autowired constructor(private val mockServer: MockRestServiceServer) :
    IntegrationTest() {

    private val observedApp = ObservedAppMockFixture(mockServer)
    private val trafficApi by lazy { TrafficApiFixture(restTestClient) }

    @BeforeEach
    fun setUp() {
        mockServer.reset()
    }

    @AfterEach
    fun tearDown() {
        trafficApi.pauseAndExpect200()
    }

    @Test
    fun `post trigger synchronously executes HAPPY_PATH scenario and increments counter`() {
        // given
        val orderId = "order-happy"
        observedApp.expectCreateOrder(scenario = HAPPY_PATH, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectPayOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectShipOrder200(scenario = HAPPY_PATH, orderId = orderId)
        observedApp.expectGetOrder200(scenario = HAPPY_PATH, orderId = orderId, state = "SHIPPED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(HAPPY_PATH)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "SHIPPED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(scenarioExecutionCounts = expectedCountsWithIncrement(HAPPY_PATH))
    }

    @Test
    fun `post trigger synchronously executes LOW_STOCK_WARNING scenario and increments counter`() {
        // given
        val orderId = "order-low-stock"
        observedApp.expectCreateOrder(scenario = LOW_STOCK_WARNING, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        observedApp.expectPayOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        observedApp.expectShipOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId)
        observedApp.expectGetOrder200(scenario = LOW_STOCK_WARNING, orderId = orderId, state = "SHIPPED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(LOW_STOCK_WARNING)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = LOW_STOCK_WARNING,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "SHIPPED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(LOW_STOCK_WARNING),
        )
    }

    @Test
    fun `post trigger synchronously executes OUT_OF_STOCK scenario and increments counter`() {
        // given
        val orderId = "order-out-of-stock"
        observedApp.expectCreateOrder(scenario = OUT_OF_STOCK, returnOrderId = orderId)
        observedApp.expectReserveOrder422(scenario = OUT_OF_STOCK, orderId = orderId)
        observedApp.expectGetOrder200(scenario = OUT_OF_STOCK, orderId = orderId, state = "FAILED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(OUT_OF_STOCK)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = OUT_OF_STOCK,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "FAILED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(scenarioExecutionCounts = expectedCountsWithIncrement(OUT_OF_STOCK))
    }

    @Test
    fun `post trigger synchronously executes PAYMENT_DECLINED scenario and increments counter`() {
        // given
        val orderId = "order-declined"
        observedApp.expectCreateOrder(scenario = PAYMENT_DECLINED, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = PAYMENT_DECLINED, orderId = orderId)
        observedApp.expectPayOrder422(scenario = PAYMENT_DECLINED, orderId = orderId)
        observedApp.expectGetOrder200(scenario = PAYMENT_DECLINED, orderId = orderId, state = "FAILED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(PAYMENT_DECLINED)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = PAYMENT_DECLINED,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "FAILED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(PAYMENT_DECLINED),
        )
    }

    @Test
    fun `post trigger synchronously executes PAYMENT_GATEWAY_TIMEOUT scenario and increments counter`() {
        // given
        val orderId = "order-timeout"
        observedApp.expectCreateOrder(scenario = PAYMENT_GATEWAY_TIMEOUT, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId)
        observedApp.expectPayOrder504(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId)
        observedApp.expectGetOrder200(scenario = PAYMENT_GATEWAY_TIMEOUT, orderId = orderId, state = "FAILED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(PAYMENT_GATEWAY_TIMEOUT)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = PAYMENT_GATEWAY_TIMEOUT,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "FAILED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(PAYMENT_GATEWAY_TIMEOUT),
        )
    }

    @Test
    fun `post trigger synchronously executes BUYER_CANCELLATION scenario and increments counter`() {
        // given
        val orderId = "order-cancellation"
        observedApp.expectCreateOrder(scenario = BUYER_CANCELLATION, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = BUYER_CANCELLATION, orderId = orderId)
        observedApp.expectCancelOrder200(scenario = BUYER_CANCELLATION, orderId = orderId)
        observedApp.expectGetOrder200(scenario = BUYER_CANCELLATION, orderId = orderId, state = "CANCELLED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(BUYER_CANCELLATION)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = BUYER_CANCELLATION,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "CANCELLED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(BUYER_CANCELLATION),
        )
    }

    @Test
    fun `post trigger synchronously executes INVALID_STATE_TRANSITION scenario and increments counter`() {
        // given
        val orderId = "order-invalid-transition"
        observedApp.expectCreateOrder(scenario = INVALID_STATE_TRANSITION, returnOrderId = orderId)
        observedApp.expectReserveOrder200(scenario = INVALID_STATE_TRANSITION, orderId = orderId)
        observedApp.expectCancelOrder200(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            reason = "cancelling before invalid pay",
        )
        observedApp.expectPayOrder409(scenario = INVALID_STATE_TRANSITION, orderId = orderId)
        observedApp.expectGetOrder200(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            state = "CANCELLED",
        )

        // when
        val response = trafficApi.triggerScenarioAndExpect200(INVALID_STATE_TRANSITION)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = INVALID_STATE_TRANSITION,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "CANCELLED",
            success = true,
        )

        // and
        observedApp.verify()

        // and
        val status = trafficApi.getStatusAndExpect200()
        status shouldBe aTrafficStatusResponse(
            scenarioExecutionCounts = expectedCountsWithIncrement(INVALID_STATE_TRANSITION),
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
        observedApp.expectGetOrder200(scenario = HAPPY_PATH, orderId = orderId, state = "SHIPPED")

        // when
        val response = trafficApi.triggerScenarioAndExpect200(HAPPY_PATH)

        // then
        response shouldBe aTriggerScenarioResponse(
            scenario = HAPPY_PATH,
            orderId = orderId,
            customerId = response.customerId,
            finalState = "SHIPPED",
            success = true,
        )

        // and
        observedApp.verify()
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
        CheckoutScenario.entries.associateWith { scenario ->
            if (scenario == incrementedScenario) 1L else 0L
        }

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

    companion object {
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
