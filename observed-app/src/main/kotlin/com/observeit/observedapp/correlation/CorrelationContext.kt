package com.observeit.observedapp.correlation

import io.opentelemetry.api.trace.Span
import org.slf4j.MDC

/**
 * Dual-enriches SLF4J [MDC] and the active OpenTelemetry [Span] with request correlation attributes.
 *
 * Reading and writing [MDC] ([SLF4J MDC Javadoc](https://www.slf4j.org/apidocs/org/slf4j/MDC.html)) is safe here
 * because each HTTP request executes synchronously on a dedicated virtual thread
 * ([JEP 444](https://openjdk.org/jeps/444), `spring.threads.virtual.enabled=true`) and `MdcCorrelationFilter` clears
 * [MDC] in a `finally` block, preventing cross-request leakage. It would *not* be safe across async/executor thread
 * hops without explicit context propagation. Read accessors exist solely to copy correlation attributes onto spans
 * and never drive business logic.
 */
object CorrelationContext {
    private const val CHECKOUT_SCENARIO = "checkout.scenario"
    private const val ORDER_ID = "order.id"
    private const val CUSTOMER_ID = "customer.id"

    fun setCheckoutScenario(scenario: String) {
        MDC.put(CHECKOUT_SCENARIO, scenario)
        Span.current().setAttribute(CHECKOUT_SCENARIO, scenario)
    }

    fun getCheckoutScenario(): String? = MDC.get(CHECKOUT_SCENARIO)

    fun setOrderId(orderId: String) {
        MDC.put(ORDER_ID, orderId)
        Span.current().setAttribute(ORDER_ID, orderId)
    }

    fun populateOrderCorrelation(orderId: String, customerId: String) {
        MDC.put(ORDER_ID, orderId)
        MDC.put(CUSTOMER_ID, customerId)
        Span.current()
            .setAttribute(ORDER_ID, orderId)
            .setAttribute(CUSTOMER_ID, customerId)
    }

    fun getCustomerId(): String? = MDC.get(CUSTOMER_ID)

    fun clear() {
        MDC.clear()
    }
}
