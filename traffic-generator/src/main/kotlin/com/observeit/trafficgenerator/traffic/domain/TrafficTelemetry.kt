package com.observeit.trafficgenerator.traffic.domain

object TrafficTelemetry {
    object Key {
        const val CHECKOUT_SCENARIO = "checkout.scenario"
        const val ORDER_STATE = "order.state"
    }

    object Metric {
        const val TRAFFIC_SCENARIOS_EXECUTED = "traffic.scenarios.executed"
        const val TRAFFIC_SCENARIO_DURATION = "traffic.scenario.duration"
        const val TRAFFIC_TARGET_RATE = "traffic.target_rate"
        const val TRAFFIC_WORKERS_ACTIVE = "traffic.workers.active"
    }

    object Units {
        const val SCENARIO = "{scenario}"
        const val MILLISECONDS = "ms"
        const val ORDER_PER_SECOND = "{order}/s"
        const val WORKER = "{worker}"
    }
}
