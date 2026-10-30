package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.traffic.api.TrafficStatusResponse
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario

object TrafficFixtures {

    fun aTrafficStatusResponse(
        running: Boolean = false,
        paused: Boolean = true,
        ordersPerSecond: Double = 1.0,
        concurrency: Int = 3,
        scenarioExecutionCounts: Map<CheckoutScenario, Long> = defaultScenarioExecutionCounts(),
    ) = TrafficStatusResponse(
        running = running,
        paused = paused,
        ordersPerSecond = ordersPerSecond,
        concurrency = concurrency,
        scenarioExecutionCounts = scenarioExecutionCounts,
    )

    private fun defaultScenarioExecutionCounts(): Map<CheckoutScenario, Long> =
        CheckoutScenario.entries.associateWith { 0L }
}
