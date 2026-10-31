package com.observeit.trafficgenerator.traffic.api

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.ScenarioExecutionResult

data class TriggerScenarioResponse(
    val scenario: CheckoutScenario,
    val orderId: String,
    val customerId: String,
    val finalState: String,
    val success: Boolean,
) {
    companion object {
        fun from(result: ScenarioExecutionResult): TriggerScenarioResponse = TriggerScenarioResponse(
            scenario = result.scenario,
            orderId = result.orderId,
            customerId = result.customerId,
            finalState = result.finalState,
            success = result.success,
        )
    }
}
