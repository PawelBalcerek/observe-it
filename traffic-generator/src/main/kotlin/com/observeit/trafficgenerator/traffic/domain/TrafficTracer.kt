package com.observeit.trafficgenerator.traffic.domain

interface ScenarioSpan {
    fun recordOrderCreated(orderId: String)

    fun recordTimeout()
}

interface TrafficTracer {
    fun traceScenario(
        scenario: CheckoutScenario,
        customerId: String,
        block: (ScenarioSpan) -> ScenarioExecutionResult,
    ): ScenarioExecutionResult
}
