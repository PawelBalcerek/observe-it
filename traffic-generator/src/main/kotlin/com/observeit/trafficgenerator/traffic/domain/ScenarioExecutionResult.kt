package com.observeit.trafficgenerator.traffic.domain

data class ScenarioExecutionResult(
    val scenario: CheckoutScenario,
    val orderId: String,
    val customerId: String,
    val finalState: String,
    val success: Boolean,
)
