package com.observeit.trafficgenerator.traffic.domain

data class TrafficStatus(
    val running: Boolean,
    val paused: Boolean,
    val ordersPerSecond: Double,
    val concurrency: Int,
    val scenarioExecutionCounts: Map<CheckoutScenario, Long>,
)
