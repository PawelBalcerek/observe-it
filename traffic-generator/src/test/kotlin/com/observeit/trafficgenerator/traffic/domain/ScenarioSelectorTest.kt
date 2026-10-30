package com.observeit.trafficgenerator.traffic.domain

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.BUYER_CANCELLATION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.INVALID_STATE_TRANSITION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.LOW_STOCK_WARNING
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ScenarioSelectorTest {

    @Test
    fun `verifies all scenarios exist and probabilities sum to 100 percent`() {
        val scenarios = CheckoutScenario.entries
        scenarios.size shouldBe EXPECTED_SCENARIO_COUNT
        scenarios.sumOf { it.probability } shouldBe (1.0 plusOrMinus PROBABILITY_SUM_TOLERANCE)
    }

    @Test
    fun `selects scenarios according to weighted probabilities over large sample`() {
        // given
        val selector = WeightedScenarioSelector(Random(42))
        val sampleSize = 50_000
        val counts = mutableMapOf<CheckoutScenario, Int>()

        // when
        repeat(sampleSize) {
            val scenario = selector.select()
            counts[scenario] = (counts[scenario] ?: 0) + 1
        }

        // then
        (counts.getValue(HAPPY_PATH).toDouble() / sampleSize) shouldBe (0.55 plusOrMinus TOLERANCE)
        (counts.getValue(LOW_STOCK_WARNING).toDouble() / sampleSize) shouldBe (0.15 plusOrMinus TOLERANCE)
        (counts.getValue(OUT_OF_STOCK).toDouble() / sampleSize) shouldBe (0.10 plusOrMinus TOLERANCE)
        (counts.getValue(PAYMENT_DECLINED).toDouble() / sampleSize) shouldBe (0.08 plusOrMinus TOLERANCE)
        (counts.getValue(PAYMENT_GATEWAY_TIMEOUT).toDouble() / sampleSize) shouldBe (0.05 plusOrMinus TOLERANCE)
        (counts.getValue(BUYER_CANCELLATION).toDouble() / sampleSize) shouldBe (0.05 plusOrMinus TOLERANCE)
        (counts.getValue(INVALID_STATE_TRANSITION).toDouble() / sampleSize) shouldBe (0.02 plusOrMinus TOLERANCE)
    }

    @Test
    fun `selects scenario deterministically based on roll threshold`() {
        // given
        fun selectorWithRoll(roll: Double) = WeightedScenarioSelector(object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = roll
        })

        // then
        selectorWithRoll(0.10).select() shouldBe HAPPY_PATH
        selectorWithRoll(0.60).select() shouldBe LOW_STOCK_WARNING
        selectorWithRoll(0.75).select() shouldBe OUT_OF_STOCK
        selectorWithRoll(0.85).select() shouldBe PAYMENT_DECLINED
        selectorWithRoll(0.90).select() shouldBe PAYMENT_GATEWAY_TIMEOUT
        selectorWithRoll(0.95).select() shouldBe BUYER_CANCELLATION
        selectorWithRoll(0.99).select() shouldBe INVALID_STATE_TRANSITION
    }

    private companion object {
        const val TOLERANCE = 0.02
        const val PROBABILITY_SUM_TOLERANCE = 0.0001
        const val EXPECTED_SCENARIO_COUNT = 7
    }
}
