package com.observeit.observedapp.order.domain

object OrderTelemetry {
    object Key {
        const val ORDER_STATE = "order.state"
        const val ORDER_PREVIOUS_STATE = "order.previous_state"
        const val ORDER_TOTAL_AMOUNT = "order.total_amount"
        const val ORDER_CURRENCY = "order.currency"
        const val ORDER_ITEM_COUNT = "order.item_count"
        const val ORDER_ATTEMPTED_ACTION = "order.attempted_action"
        const val INVENTORY_SKU = "inventory.sku"
        const val INVENTORY_REQUESTED_QUANTITY = "inventory.requested_quantity"
        const val INVENTORY_AVAILABLE_QUANTITY = "inventory.available_quantity"
        const val INVENTORY_OUTCOME = "inventory.outcome"
        const val PAYMENT_METHOD = "payment.method"
        const val PAYMENT_OUTCOME = "payment.outcome"
        const val PAYMENT_LATENCY_MS = "payment.latency_ms"
        const val SHIPPING_METHOD = "shipping.method"
        const val CANCELLATION_REASON = "cancellation.reason"
        const val ERROR_TYPE = "error.type"
    }

    object ErrorType {
        const val INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION"
        const val OUT_OF_STOCK = "OUT_OF_STOCK"
        const val PAYMENT_DECLINED = "PAYMENT_DECLINED"
        const val GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT"
    }

    object Metric {
        const val ORDERS_CREATED = "orders.created"
        const val ORDER_STATE_TRANSITIONS = "order.state.transitions"
        const val ORDER_ERRORS = "order.errors"
        const val ORDER_INVENTORY_RESERVATIONS = "order.inventory.reservations"
        const val ORDER_PAYMENT_DURATION = "order.payment.duration"
        const val ORDER_REPOSITORY_SIZE = "order.repository.size"
    }

    object Units {
        const val ORDER = "{order}"
        const val TRANSITION = "{transition}"
        const val ERROR = "{error}"
        const val RESERVATION = "{reservation}"
        const val MILLISECONDS = "ms"
    }
}
