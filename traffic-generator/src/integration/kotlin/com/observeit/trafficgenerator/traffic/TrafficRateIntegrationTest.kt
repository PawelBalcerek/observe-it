package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.IntegrationTest
import com.observeit.trafficgenerator.test.LogCapture
import com.observeit.trafficgenerator.test.shouldBeValidationError
import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTrafficStatusResponse
import com.observeit.trafficgenerator.traffic.TrafficFixtures.anUpdateTrafficRateRequest
import com.observeit.trafficgenerator.traffic.TrafficFixtures.anUpdateTrafficRateResponse
import com.observeit.trafficgenerator.traffic.api.TrafficApiFixture
import com.observeit.trafficgenerator.traffic.api.UpdateTrafficRateRequest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource

class TrafficRateIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val trafficApi by lazy { TrafficApiFixture(restTestClient) }

    @BeforeEach
    fun setUp() {
        resetTrafficControl()
        metricCapture.drainAndReset()
    }

    @AfterEach
    fun tearDown() {
        resetTrafficControl()
    }

    @Test
    fun `post rate updates both ordersPerSecond and concurrency, reflects in status, and emits log`() {
        // given
        val request = anUpdateTrafficRateRequest(ordersPerSecond = 2.5)

        // when
        val rateResponse = trafficApi.updateRateAndExpect200(request)
        metricCapture.drainAndReset()

        // then
        rateResponse shouldBe anUpdateTrafficRateResponse(ordersPerSecond = 2.5, concurrency = 5)

        // and
        val statusResponse = trafficApi.getStatusAndExpect200()
        statusResponse shouldBe aTrafficStatusResponse(ordersPerSecond = 2.5, concurrency = 5)
        metricCapture.assertWorkerGauges(targetRate = 2.5, activeWorkers = 0L)

        // and
        logCapture.assertTrafficRateUpdatedLog(ordersPerSecond = 2.5, concurrency = 5)
    }

    @Test
    fun `post rate updates only ordersPerSecond when concurrency is null, reflects in status, and emits log`() {
        // given
        val request = anUpdateTrafficRateRequest(ordersPerSecond = 4.0, concurrency = null)

        // when
        val rateResponse = trafficApi.updateRateAndExpect200(request)
        metricCapture.drainAndReset()

        // then
        rateResponse shouldBe anUpdateTrafficRateResponse(ordersPerSecond = 4.0, concurrency = 3)

        // and
        val statusResponse = trafficApi.getStatusAndExpect200()
        statusResponse shouldBe aTrafficStatusResponse(ordersPerSecond = 4.0, concurrency = 3)
        metricCapture.assertWorkerGauges(targetRate = 4.0, activeWorkers = 0L)

        // and
        logCapture.assertTrafficRateUpdatedLog(ordersPerSecond = 4.0, concurrency = 3)
    }

    @Test
    fun `post rate updates only concurrency when ordersPerSecond is null, reflects in status, and emits log`() {
        // given
        val request = anUpdateTrafficRateRequest(ordersPerSecond = null, concurrency = 6)

        // when
        val rateResponse = trafficApi.updateRateAndExpect200(request)
        metricCapture.drainAndReset()

        // then
        rateResponse shouldBe anUpdateTrafficRateResponse(ordersPerSecond = 1.0, concurrency = 6)

        // and
        val statusResponse = trafficApi.getStatusAndExpect200()
        statusResponse shouldBe aTrafficStatusResponse(ordersPerSecond = 1.0, concurrency = 6)
        metricCapture.assertWorkerGauges(targetRate = 1.0, activeWorkers = 0L)

        // and
        logCapture.assertTrafficRateUpdatedLog(ordersPerSecond = 1.0, concurrency = 6)
    }

    @Test
    fun `post rate updates target rate and active workers gauges when running`() {
        // given
        trafficApi.resumeAndExpect200()

        // when
        val rateResponse = trafficApi.updateRateAndExpect200(anUpdateTrafficRateRequest(ordersPerSecond = 4.5))
        metricCapture.drainAndReset()

        // then
        rateResponse shouldBe anUpdateTrafficRateResponse(ordersPerSecond = 4.5, concurrency = 5)

        // and
        metricCapture.assertWorkerGauges(targetRate = 4.5, activeWorkers = 5L)
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("invalidRateRequests")
    fun `post rate returns 400 Bad Request when request validation fails`(
        description: String,
        request: UpdateTrafficRateRequest,
        expectedError: String,
    ) {
        // when
        val problem = trafficApi.updateRateAndExpect400(request)

        // then
        problem.shouldBeValidationError(expectedError)
    }

    private fun resetTrafficControl() {
        trafficApi.pauseAndExpect200()
        trafficApi.updateRateAndExpect200(
            anUpdateTrafficRateRequest(
                ordersPerSecond = DEFAULT_ORDERS_PER_SECOND,
                concurrency = DEFAULT_CONCURRENCY,
            ),
        )
    }

    companion object {
        private const val DEFAULT_ORDERS_PER_SECOND = 1.0
        private const val DEFAULT_CONCURRENCY = 3

        @JvmStatic
        val invalidRateRequests = listOf(
            Arguments.of(
                "ordersPerSecond is zero",
                anUpdateTrafficRateRequest(ordersPerSecond = 0.0),
                "ordersPerSecond must be greater than 0",
            ),
            Arguments.of(
                "ordersPerSecond is negative",
                anUpdateTrafficRateRequest(ordersPerSecond = -1.0),
                "ordersPerSecond must be greater than 0",
            ),
            Arguments.of(
                "concurrency is zero",
                anUpdateTrafficRateRequest(concurrency = 0),
                "concurrency must be greater than 0",
            ),
            Arguments.of(
                "concurrency is negative",
                anUpdateTrafficRateRequest(concurrency = -2),
                "concurrency must be greater than 0",
            ),
            Arguments.of(
                "neither ordersPerSecond nor concurrency provided",
                anUpdateTrafficRateRequest(ordersPerSecond = null, concurrency = null),
                "At least one of ordersPerSecond or concurrency must be provided",
            ),
        )
    }
}
