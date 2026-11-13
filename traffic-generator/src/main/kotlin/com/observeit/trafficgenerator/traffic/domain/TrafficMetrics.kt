package com.observeit.trafficgenerator.traffic.domain

interface TrafficMetrics {
    fun recordScenarioExecuted(scenario: CheckoutScenario, finalState: String)

    fun recordScenarioDuration(scenario: CheckoutScenario, durationMs: Double)

    fun registerWorkerGauges(ordersPerSecond: () -> Double, activeWorkers: () -> Long)
}
