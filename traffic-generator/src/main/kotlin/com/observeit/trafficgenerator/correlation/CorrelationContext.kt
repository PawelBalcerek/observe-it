package com.observeit.trafficgenerator.correlation

import org.slf4j.MDC

object CorrelationContext {
    private const val CHECKOUT_SCENARIO = "checkout.scenario"
    private const val ORDER_ID = "order.id"
    private const val CUSTOMER_ID = "customer.id"

    fun setCheckoutScenario(scenario: String) {
        MDC.put(CHECKOUT_SCENARIO, scenario)
    }

    fun setOrderId(orderId: String) {
        MDC.put(ORDER_ID, orderId)
    }

    fun setCustomerId(customerId: String) {
        MDC.put(CUSTOMER_ID, customerId)
    }

    fun clear() {
        MDC.clear()
    }
}
