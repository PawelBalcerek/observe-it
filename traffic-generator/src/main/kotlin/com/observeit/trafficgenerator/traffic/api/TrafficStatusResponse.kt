package com.observeit.trafficgenerator.traffic.api

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.TrafficStatus

data class TrafficStatusResponse(
    val running: Boolean,
    val paused: Boolean,
    val ordersPerSecond: Double,
    val concurrency: Int,
    val scenarioExecutionCounts: Map<CheckoutScenario, Long>,
) {
    companion object {
        fun from(status: TrafficStatus): TrafficStatusResponse = TrafficStatusResponse(
            running = status.running,
            paused = status.paused,
            ordersPerSecond = status.ordersPerSecond,
            concurrency = status.concurrency,
            scenarioExecutionCounts = status.scenarioExecutionCounts,
        )
    }
}
