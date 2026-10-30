package com.observeit.trafficgenerator.traffic.domain

import org.springframework.stereotype.Component
import kotlin.random.Random

fun interface ScenarioSelector {
    fun select(): CheckoutScenario
}

/**
 * Weighted selector implementing discrete inverse-transform sampling (linear scan over the cumulative
 * distribution function, also known as fitness-proportionate / roulette-wheel selection; see Luc Devroye,
 * *Non-Uniform Random Variate Generation*, Ch. III §2.1 "The inversion method",
 * https://luc.devroye.org/rnbookindex.html, and
 * https://en.wikipedia.org/wiki/Fitness_proportionate_selection).
 *
 * Given a uniform random number `r` in `[0, 1)`, the selector accumulates scenario probabilities sequentially
 * and returns the first scenario whose cumulative probability upper bound exceeds `r`.
 * The fallback to [CheckoutScenario.entries.last] guards against IEEE 754 floating-point rounding
 * where the cumulative sum evaluates slightly below `1.0` while `r` is near `1.0`.
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
