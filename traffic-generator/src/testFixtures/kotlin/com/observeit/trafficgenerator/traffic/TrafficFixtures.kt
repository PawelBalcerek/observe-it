package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.traffic.api.TrafficStatusResponse
import com.observeit.trafficgenerator.traffic.api.UpdateTrafficRateRequest
import com.observeit.trafficgenerator.traffic.api.UpdateTrafficRateResponse
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.TrafficRate

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

    fun aTrafficRate(ordersPerSecond: Double = 1.0, concurrency: Int = 3) = TrafficRate(
        ordersPerSecond = ordersPerSecond,
        concurrency = concurrency,
    )

    fun anUpdateTrafficRateRequest(ordersPerSecond: Double? = 2.0, concurrency: Int? = 5) = UpdateTrafficRateRequest(
        ordersPerSecond = ordersPerSecond,
        concurrency = concurrency,
    )

    fun anUpdateTrafficRateResponse(ordersPerSecond: Double = 2.0, concurrency: Int = 5) = UpdateTrafficRateResponse(
        ordersPerSecond = ordersPerSecond,
        concurrency = concurrency,
    )

    private fun defaultScenarioExecutionCounts(): Map<CheckoutScenario, Long> =
        CheckoutScenario.entries.associateWith { 0L }
}
