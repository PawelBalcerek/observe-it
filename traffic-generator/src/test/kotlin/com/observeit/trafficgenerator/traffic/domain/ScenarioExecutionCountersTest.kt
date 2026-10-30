package com.observeit.trafficgenerator.traffic.domain

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.BUYER_CANCELLATION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.INVALID_STATE_TRANSITION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.LOW_STOCK_WARNING
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class ScenarioExecutionCountersTest {

    @Test
    fun `initializes all 7 canonical scenarios with zero counts`() {
        // given
        val counters = ScenarioExecutionCounters()

        // when
        val counts = counters.getCounts()

        // then
        counts shouldBe mapOf(
            HAPPY_PATH to 0L,
            LOW_STOCK_WARNING to 0L,
            OUT_OF_STOCK to 0L,
            PAYMENT_DECLINED to 0L,
            PAYMENT_GATEWAY_TIMEOUT to 0L,
            BUYER_CANCELLATION to 0L,
            INVALID_STATE_TRANSITION to 0L,
        )
    }

    @Test
    fun `increments scenario counter thread-safely across concurrent executions`() {
        // given
        val counters = ScenarioExecutionCounters()
        val totalTasks = 100
        val threadPool = Executors.newFixedThreadPool(10)

        // when
        try {
            repeat(totalTasks) { index ->
                threadPool.submit {
                    val scenario = if (index % 2 == 0) HAPPY_PATH else PAYMENT_DECLINED
                    counters.increment(scenario)
                }
            }
        } finally {
            threadPool.shutdown()
            threadPool.awaitTermination(5, SECONDS)
        }

        // then
        val counts = counters.getCounts()
        counts[HAPPY_PATH] shouldBe 50L
        counts[PAYMENT_DECLINED] shouldBe 50L
        counts[OUT_OF_STOCK] shouldBe 0L
    }
}
