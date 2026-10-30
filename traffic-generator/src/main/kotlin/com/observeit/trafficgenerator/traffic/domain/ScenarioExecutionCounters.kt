package com.observeit.trafficgenerator.traffic.domain

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.LongAdder

/**
 * Thread-safe execution counters per [CheckoutScenario].
 *
 * Uses [LongAdder]
 * (https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)
 * for lock-free, highly concurrent counter increments across virtual-thread workers.
 */
@Component
class ScenarioExecutionCounters {

    private val counters: Map<CheckoutScenario, LongAdder> =
        CheckoutScenario.entries.associateWith { LongAdder() }

    fun increment(scenario: CheckoutScenario) {
        counters.getValue(scenario).increment()
    }

    fun getCounts(): Map<CheckoutScenario, Long> = counters.mapValues { it.value.sum() }
}
