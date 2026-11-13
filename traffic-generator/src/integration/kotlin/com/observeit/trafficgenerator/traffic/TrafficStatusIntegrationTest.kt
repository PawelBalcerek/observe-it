package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.IntegrationTest
import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTrafficStatusResponse
import com.observeit.trafficgenerator.traffic.api.TrafficApiFixture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.client.MockRestServiceServer

@Import(TestObservedAppClientConfiguration::class)
class TrafficStatusIntegrationTest @Autowired constructor(private val mockServer: MockRestServiceServer) :
    IntegrationTest() {

    private val observedApp = ObservedAppMockFixture(mockServer)
    private val trafficApi by lazy { TrafficApiFixture(restTestClient) }
    private var baselineCounts: Map<CheckoutScenario, Long> = emptyMap()

    @BeforeEach
    fun setUp() {
        mockServer.reset()
        baselineCounts = trafficApi.getStatusAndExpect200().scenarioExecutionCounts
    }

    @Test
    fun `returns traffic status with all 7 scenarios and paused background generation`() {
        // given
        val expected = aTrafficStatusResponse(scenarioExecutionCounts = baselineCounts)

        // when
        val response = trafficApi.getStatusAndExpect200()

        // then
        response shouldBe expected
        response.scenarioExecutionCounts.keys shouldBe CheckoutScenario.entries.toSet()
    }

    @Test
    fun `increments scenario execution counters after completing checkout scenarios`() {
        // given
        val happyPathOrderId = "order-happy"
        val paymentDeclinedOrderId = "order-declined"

        observedApp.expectCreateOrder(scenario = HAPPY_PATH, returnOrderId = happyPathOrderId)
        observedApp.expectReserveOrder200(scenario = HAPPY_PATH, orderId = happyPathOrderId)
        observedApp.expectPayOrder200(scenario = HAPPY_PATH, orderId = happyPathOrderId)
        observedApp.expectShipOrder200(scenario = HAPPY_PATH, orderId = happyPathOrderId)
        observedApp.expectGetOrder200(scenario = HAPPY_PATH, orderId = happyPathOrderId)

        observedApp.expectCreateOrder(scenario = PAYMENT_DECLINED, returnOrderId = paymentDeclinedOrderId)
        observedApp.expectReserveOrder200(scenario = PAYMENT_DECLINED, orderId = paymentDeclinedOrderId)
        observedApp.expectPayOrder422(scenario = PAYMENT_DECLINED, orderId = paymentDeclinedOrderId)
        observedApp.expectGetOrder200(scenario = PAYMENT_DECLINED, orderId = paymentDeclinedOrderId, state = "FAILED")

        // when
        trafficApi.triggerScenarioAndExpect200(HAPPY_PATH)
        trafficApi.triggerScenarioAndExpect200(PAYMENT_DECLINED)
        val response = trafficApi.getStatusAndExpect200()

        // then
        val expectedCounts = baselineCounts +
            (HAPPY_PATH to baselineCounts.getValue(HAPPY_PATH) + 1L) +
            (PAYMENT_DECLINED to baselineCounts.getValue(PAYMENT_DECLINED) + 1L)
        response shouldBe aTrafficStatusResponse(scenarioExecutionCounts = expectedCounts)

        // and
        observedApp.verify()
    }
}
