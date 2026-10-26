package com.observeit.observedapp.correlation

import org.slf4j.MDC

object CorrelationContext {
    const val CHECKOUT_SCENARIO = "checkout.scenario"
    const val ORDER_ID = "order.id"
    const val CUSTOMER_ID = "customer.id"

    fun setCheckoutScenario(scenario: String) {
        MDC.put(CHECKOUT_SCENARIO, scenario)
    }

    fun populateOrderCorrelation(orderId: String, customerId: String) {
        MDC.put(ORDER_ID, orderId)
        MDC.put(CUSTOMER_ID, customerId)
    }

    fun clear() {
        MDC.clear()
    }
}
