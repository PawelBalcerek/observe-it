package com.observeit.trafficgenerator.traffic.domain

object TrafficTelemetry {
    object Key {
        const val CHECKOUT_SCENARIO = "checkout.scenario"
        const val CUSTOMER_ID = "customer.id"
        const val ORDER_ID = "order.id"
        const val ORDER_STATE = "order.state"
        const val ORDER_PREVIOUS_STATE = "order.previous_state"
        const val ORDER_TOTAL_AMOUNT = "order.total_amount"
        const val ORDER_CURRENCY = "order.currency"
        const val ORDER_ITEM_COUNT = "order.item_count"
        const val ORDER_ATTEMPTED_ACTION = "order.attempted_action"
        const val INVENTORY_SKU = "inventory.sku"
        const val PAYMENT_METHOD = "payment.method"
        const val SHIPPING_METHOD = "shipping.method"
        const val CANCELLATION_REASON = "cancellation.reason"
        const val ERROR_TYPE = "error.type"
    }

    object ErrorType {
        const val OUT_OF_STOCK = "OUT_OF_STOCK"
        const val PAYMENT_DECLINED = "PAYMENT_DECLINED"
        const val GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT"
        const val INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION"
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
