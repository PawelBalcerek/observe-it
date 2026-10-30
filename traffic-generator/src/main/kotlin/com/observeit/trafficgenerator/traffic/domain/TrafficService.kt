package com.observeit.trafficgenerator.traffic.domain

import org.springframework.stereotype.Service

@Service
class TrafficService(
    private val workerControl: TrafficWorkerControl,
    private val scenarioExecutionCounters: ScenarioExecutionCounters,
) {

    fun getStatus(): TrafficStatus {
        val running = workerControl.isRunning()
        return TrafficStatus(
            running = running,
            paused = !running,
            ordersPerSecond = workerControl.ordersPerSecond,
            concurrency = workerControl.concurrency,
            scenarioExecutionCounts = scenarioExecutionCounters.getCounts(),
        )
    }
}
