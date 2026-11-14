package com.observeit.trafficgenerator.traffic

import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import ch.qos.logback.classic.spi.ILoggingEvent
import com.observeit.trafficgenerator.test.LogCapture
import com.observeit.trafficgenerator.test.MetricCapture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.PointData

private object Key {
    const val CHECKOUT_SCENARIO = "checkout.scenario"
    const val ORDER_ID = "order.id"
    const val CUSTOMER_ID = "customer.id"
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
    const val TRAFFIC_ORDERS_PER_SECOND = "traffic.orders_per_second"
    const val TRAFFIC_CONCURRENCY = "traffic.concurrency"
    const val TRAFFIC_RUNNING = "traffic.running"
    const val TRAFFIC_PAUSED = "traffic.paused"
}

private object Metric {
    const val TRAFFIC_TARGET_RATE = "traffic.target_rate"
    const val TRAFFIC_WORKERS_ACTIVE = "traffic.workers.active"
    const val TRAFFIC_SCENARIOS_EXECUTED = "traffic.scenarios.executed"
    const val TRAFFIC_SCENARIO_DURATION = "traffic.scenario.duration"
}

private object Unit {
    const val ORDER_PER_SECOND = "{order}/s"
    const val WORKER = "{worker}"
    const val SCENARIO = "{scenario}"
    const val MILLISECONDS = "ms"
}

private object State {
    const val CREATED = "CREATED"
    const val RESERVED = "RESERVED"
    const val PAID = "PAID"
    const val SHIPPED = "SHIPPED"
    const val CANCELLED = "CANCELLED"
}

private object Method {
    const val CREDIT_CARD = "CREDIT_CARD"
    const val DECLINED_CARD = "DECLINED_CARD"
    const val TIMEOUT_GATEWAY = "TIMEOUT_GATEWAY"
    const val STANDARD = "STANDARD"
}

private object ErrorType {
    const val OUT_OF_STOCK = "OUT_OF_STOCK"
    const val PAYMENT_DECLINED = "PAYMENT_DECLINED"
    const val GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT"
    const val INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION"
}

private fun expectedScenarioMdc(scenario: CheckoutScenario, orderId: String, customerId: String): Map<String, String> =
    mapOf(
        Key.CHECKOUT_SCENARIO to scenario.name,
        Key.ORDER_ID to orderId,
        Key.CUSTOMER_ID to customerId,
    )

fun MetricCapture.assertWorkerGauges(targetRate: Double, activeWorkers: Long) {
    assertGaugeValue(
        name = Metric.TRAFFIC_TARGET_RATE,
        unit = Unit.ORDER_PER_SECOND,
        value = targetRate,
    )
    assertGaugeValue(
        name = Metric.TRAFFIC_WORKERS_ACTIVE,
        unit = Unit.WORKER,
        value = activeWorkers,
    )
}

fun MetricCapture.assertScenarioExecutedIncremented(
    scenario: CheckoutScenario,
    finalState: String,
    delta: Long = 1L,
): PointData = assertCounterIncremented(
    name = Metric.TRAFFIC_SCENARIOS_EXECUTED,
    unit = Unit.SCENARIO,
    delta = delta,
    attributes = mapOf(
        Key.CHECKOUT_SCENARIO to scenario.name,
        Key.ORDER_STATE to finalState,
    ),
)

fun MetricCapture.assertScenarioDurationRecorded(scenario: CheckoutScenario, count: Long = 1L): HistogramPointData {
    val point = assertHistogramRecorded(
        name = Metric.TRAFFIC_SCENARIO_DURATION,
        unit = Unit.MILLISECONDS,
        count = count,
        attributes = mapOf(Key.CHECKOUT_SCENARIO to scenario.name),
    )
    point.sum shouldBeGreaterThanOrEqualTo 0.0
    return point
}

fun LogCapture.assertOrderCreatedLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    state: String = State.CREATED,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order created",
    keyValues = mapOf(
        Key.ORDER_ID to orderId,
        Key.ORDER_STATE to state,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertOrderReservedLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    state: String = State.RESERVED,
    previousState: String = State.CREATED,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order reserved",
    keyValues = mapOf(
        Key.ORDER_STATE to state,
        Key.ORDER_PREVIOUS_STATE to previousState,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertOutOfStockLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    sku: String = "SKU-OUT-OF-STOCK",
    errorType: String = ErrorType.OUT_OF_STOCK,
): ILoggingEvent = assertLogged(
    level = WARN,
    message = "Order reservation failed due to out of stock",
    keyValues = mapOf(
        Key.ERROR_TYPE to errorType,
        Key.INVENTORY_SKU to sku,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertOrderPaidLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    paymentMethod: String = Method.CREDIT_CARD,
    state: String = State.PAID,
    previousState: String = State.RESERVED,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order paid",
    keyValues = mapOf(
        Key.ORDER_STATE to state,
        Key.ORDER_PREVIOUS_STATE to previousState,
        Key.PAYMENT_METHOD to paymentMethod,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertPaymentDeclinedLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    paymentMethod: String = Method.DECLINED_CARD,
    errorType: String = ErrorType.PAYMENT_DECLINED,
): ILoggingEvent = assertLogged(
    level = WARN,
    message = "Payment declined for method $paymentMethod",
    keyValues = mapOf(
        Key.ERROR_TYPE to errorType,
        Key.PAYMENT_METHOD to paymentMethod,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertPaymentGatewayTimeoutLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    paymentMethod: String = Method.TIMEOUT_GATEWAY,
    errorType: String = ErrorType.GATEWAY_TIMEOUT,
): ILoggingEvent = assertLogged(
    level = ERROR,
    message = "Payment gateway timed out",
    keyValues = mapOf(
        Key.ERROR_TYPE to errorType,
        Key.PAYMENT_METHOD to paymentMethod,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertOrderShippedLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    shippingMethod: String = Method.STANDARD,
    state: String = State.SHIPPED,
    previousState: String = State.PAID,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order shipped",
    keyValues = mapOf(
        Key.ORDER_STATE to state,
        Key.ORDER_PREVIOUS_STATE to previousState,
        Key.SHIPPING_METHOD to shippingMethod,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertOrderCancelledLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    reason: String = "buyer cancellation request",
    state: String = State.CANCELLED,
    previousState: String = State.RESERVED,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order cancelled",
    keyValues = mapOf(
        Key.ORDER_STATE to state,
        Key.ORDER_PREVIOUS_STATE to previousState,
        Key.CANCELLATION_REASON to reason,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertInvalidStateTransitionLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    attemptedAction: String = "pay",
    state: String = State.CANCELLED,
    errorType: String = ErrorType.INVALID_STATE_TRANSITION,
): ILoggingEvent = assertLogged(
    level = WARN,
    message = "Cannot $attemptedAction order in $state state",
    keyValues = mapOf(
        Key.ERROR_TYPE to errorType,
        Key.ORDER_ATTEMPTED_ACTION to attemptedAction,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertOrderRetrievedLog(
    scenario: CheckoutScenario,
    orderId: String,
    customerId: String,
    state: String,
    totalAmount: Double = 10.0,
    currency: String = "USD",
    itemCount: Long = 1L,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order retrieved",
    keyValues = mapOf(
        Key.ORDER_STATE to state,
        Key.ORDER_TOTAL_AMOUNT to totalAmount,
        Key.ORDER_CURRENCY to currency,
        Key.ORDER_ITEM_COUNT to itemCount,
    ),
    mdc = expectedScenarioMdc(scenario, orderId, customerId),
)

fun LogCapture.assertTrafficRateUpdatedLog(ordersPerSecond: Double, concurrency: Int): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Traffic generator rate updated",
    keyValues = mapOf(
        Key.TRAFFIC_ORDERS_PER_SECOND to ordersPerSecond,
        Key.TRAFFIC_CONCURRENCY to concurrency,
    ),
)

fun LogCapture.assertTrafficResumedLog(): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Traffic generator resumed",
    keyValues = mapOf(
        Key.TRAFFIC_RUNNING to true,
        Key.TRAFFIC_PAUSED to false,
    ),
)

fun LogCapture.assertTrafficPausedLog(): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Traffic generator paused",
    keyValues = mapOf(
        Key.TRAFFIC_RUNNING to false,
        Key.TRAFFIC_PAUSED to true,
    ),
)
