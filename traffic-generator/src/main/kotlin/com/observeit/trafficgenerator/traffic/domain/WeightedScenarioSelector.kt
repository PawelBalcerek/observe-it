package com.observeit.trafficgenerator.traffic.domain

import org.springframework.stereotype.Component
import kotlin.random.Random

fun interface ScenarioSelector {
    fun select(): CheckoutScenario
}

/**
 * Weighted selector implementing Cumulative Distribution Function (CDF) inverse transform sampling
 * (also known as Walker's roulette-wheel selection algorithm; Knuth, The Art of Computer Programming,
 * Vol. 2: Seminumerical Algorithms §3.4.1; Cormen et al., Introduction to Algorithms).
 *
 * Given a uniform random number r in [0, 1), the selector accumulates scenario probabilities sequentially
 * and selects the first scenario whose cumulative probability upper bound exceeds r.
 * The fallback to [CheckoutScenario.entries.last] guards against IEEE 754 floating-point rounding
 * where the cumulative sum may evaluate to 0.9999999999999999 while r evaluates to 0.99999999999999995.
 */
@Component
class WeightedScenarioSelector(private val random: Random = Random.Default) : ScenarioSelector {

    override fun select(): CheckoutScenario {
        val roll = random.nextDouble()
        var cumulative = 0.0
        for (scenario in CheckoutScenario.entries) {
            cumulative += scenario.probability
            if (roll < cumulative) {
                return scenario
            }
        }
        return CheckoutScenario.entries.last()
    }
}
