package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.IntegrationTest
import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTrafficStatusResponse
import com.observeit.trafficgenerator.traffic.api.TrafficApiFixture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
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
@Import(TrafficStatusIntegrationTest.MockServerConfig::class)
class TrafficStatusIntegrationTest @Autowired constructor(
    private val mockServer: MockRestServiceServer,
    private val runner: CheckoutScenarioRunner,
) : IntegrationTest() {

    private val observedApp = ObservedAppMockFixture(mockServer)
    private val trafficApi by lazy { TrafficApiFixture(restTestClient) }

    @BeforeEach
    fun setUp() {
        mockServer.reset()
    }

    @Test
    fun `returns initial traffic status with all 7 scenarios at zero counts and paused background generation`() {
        // given
        val expected = aTrafficStatusResponse()

        // when
        val response = trafficApi.getStatusAndExpect200()

        // then
        response shouldBe expected
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
        runner.execute(HAPPY_PATH)
        runner.execute(PAYMENT_DECLINED)
        val response = trafficApi.getStatusAndExpect200()

        // then
        val expectedCounts = CheckoutScenario.entries.associateWith { scenario ->
            when (scenario) {
                HAPPY_PATH, PAYMENT_DECLINED -> 1L
                LOW_STOCK_WARNING,
                OUT_OF_STOCK,
                PAYMENT_GATEWAY_TIMEOUT,
                BUYER_CANCELLATION,
                INVALID_STATE_TRANSITION,
                -> 0L
            }
        }
        response shouldBe aTrafficStatusResponse(scenarioExecutionCounts = expectedCounts)

        // and
        observedApp.verify()
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
}
