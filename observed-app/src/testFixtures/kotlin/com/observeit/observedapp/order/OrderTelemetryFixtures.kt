package com.observeit.observedapp.order

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.WARN
import ch.qos.logback.classic.spi.ILoggingEvent
import com.observeit.observedapp.order.domain.InventoryOutcome
import com.observeit.observedapp.order.domain.OrderState
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.order.domain.PaymentOutcome
import com.observeit.observedapp.order.domain.PaymentOutcome.SUCCESS
import com.observeit.observedapp.order.domain.PaymentOutcome.TIMEOUT
import com.observeit.observedapp.order.domain.ShippingMethod
import com.observeit.observedapp.order.domain.ShippingMethod.STANDARD
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.MetricCapture
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

private object Metric {
    const val ORDERS_CREATED = "orders.created"
    const val ORDER_REPOSITORY_SIZE = "order.repository.size"
    const val ORDER_STATE_TRANSITIONS = "order.state.transitions"
    const val ORDER_INVENTORY_RESERVATIONS = "order.inventory.reservations"
    const val ORDER_PAYMENT_DURATION = "order.payment.duration"
    const val ORDER_ERRORS = "order.errors"
}

private object Unit {
    const val ORDER = "{order}"
    const val TRANSITION = "{transition}"
    const val RESERVATION = "{reservation}"
    const val MILLISECONDS = "ms"
    const val ERROR = "{error}"
}

private object ErrorType {
    const val OUT_OF_STOCK = "OUT_OF_STOCK"
    const val PAYMENT_DECLINED = "PAYMENT_DECLINED"
    const val GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT"
    const val INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION"
}

private fun expectedOrderMdc(
    orderId: String,
    customerId: String? = null,
    checkoutScenario: String?,
): Map<String, String> = buildMap {
    if (checkoutScenario != null) {
        put(Key.CHECKOUT_SCENARIO, checkoutScenario)
    }
    put(Key.ORDER_ID, orderId)
    if (customerId != null) {
        put(Key.CUSTOMER_ID, customerId)
    }
}

fun LogCapture.assertOrderCreatedLog(
    orderId: String,
    customerId: String = "customerId",
    totalAmount: Double = 10.0,
    currency: String = "USD",
    itemCount: Long = 1L,
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order created",
    keyValues = mapOf(
        Key.ORDER_STATE to CREATED.name,
        Key.ORDER_TOTAL_AMOUNT to totalAmount,
        Key.ORDER_CURRENCY to currency,
        Key.ORDER_ITEM_COUNT to itemCount,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertOrderRetrievedLog(
    orderId: String,
    state: OrderState = CREATED,
    customerId: String = "customerId",
    totalAmount: Double = 10.0,
    currency: String = "USD",
    itemCount: Long = 1L,
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order retrieved",
    keyValues = mapOf(
        Key.ORDER_STATE to state.name,
        Key.ORDER_TOTAL_AMOUNT to totalAmount,
        Key.ORDER_CURRENCY to currency,
        Key.ORDER_ITEM_COUNT to itemCount,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertInventoryReservedLog(
    orderId: String,
    sku: String = "sku",
    requestedQuantity: Long = 1L,
    availableQuantity: Long = 100L,
    customerId: String = "customerId",
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = DEBUG,
    message = "Inventory reserved for $sku ($requestedQuantity/$availableQuantity)",
    keyValues = mapOf(
        Key.INVENTORY_SKU to sku,
        Key.INVENTORY_REQUESTED_QUANTITY to requestedQuantity,
        Key.INVENTORY_AVAILABLE_QUANTITY to availableQuantity,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertLowStockWarningLog(
    orderId: String,
    sku: String = "SKU-LOW-STOCK",
    requestedQuantity: Long = 1L,
    availableQuantity: Long = 2L,
    customerId: String = "customerId",
    checkoutScenario: String? = null,
): ILoggingEvent {
    val event = assertLogged(
        level = WARN,
        message = "Low stock for $sku ($requestedQuantity/$availableQuantity)",
        keyValues = mapOf(
            Key.INVENTORY_SKU to sku,
            Key.INVENTORY_REQUESTED_QUANTITY to requestedQuantity,
            Key.INVENTORY_AVAILABLE_QUANTITY to availableQuantity,
        ),
        mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
    )
    event.throwableProxy shouldBe null
    return event
}

fun LogCapture.assertOutOfStockLog(
    orderId: String,
    sku: String = "SKU-OUT-OF-STOCK",
    requestedQuantity: Long = 1L,
    availableQuantity: Long = 0L,
    errorType: String = ErrorType.OUT_OF_STOCK,
    customerId: String = "customerId",
    checkoutScenario: String?,
): ILoggingEvent {
    val event = assertLogged(
        level = WARN,
        message = "Inventory out of stock for $sku",
        keyValues = mapOf(
            Key.ERROR_TYPE to errorType,
            Key.INVENTORY_SKU to sku,
            Key.INVENTORY_REQUESTED_QUANTITY to requestedQuantity,
            Key.INVENTORY_AVAILABLE_QUANTITY to availableQuantity,
        ),
        mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
    )
    event.throwableProxy shouldBe null
    return event
}

fun LogCapture.assertOrderReservedLog(
    orderId: String,
    previousState: OrderState = CREATED,
    customerId: String = "customerId",
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order reserved",
    keyValues = mapOf(
        Key.ORDER_STATE to RESERVED.name,
        Key.ORDER_PREVIOUS_STATE to previousState.name,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertOrderPaidLog(
    orderId: String,
    paymentMethod: PaymentMethod = CREDIT_CARD,
    latencyMs: Long = 50L,
    previousState: OrderState = RESERVED,
    customerId: String = "customerId",
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order paid",
    keyValues = mapOf(
        Key.ORDER_STATE to PAID.name,
        Key.ORDER_PREVIOUS_STATE to previousState.name,
        Key.PAYMENT_METHOD to paymentMethod.name,
        Key.PAYMENT_LATENCY_MS to latencyMs,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertPaymentDeclinedLog(
    orderId: String,
    paymentMethod: PaymentMethod = DECLINED_CARD,
    latencyMs: Long = 50L,
    errorType: String = ErrorType.PAYMENT_DECLINED,
    customerId: String = "customerId",
    checkoutScenario: String?,
): ILoggingEvent {
    val event = assertLogged(
        level = WARN,
        message = "Payment declined for method ${paymentMethod.name}",
        keyValues = mapOf(
            Key.ERROR_TYPE to errorType,
            Key.PAYMENT_METHOD to paymentMethod.name,
            Key.PAYMENT_LATENCY_MS to latencyMs,
        ),
        mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
    )
    event.throwableProxy shouldBe null
    return event
}

fun LogCapture.assertPaymentGatewayTimeoutLog(
    orderId: String,
    paymentMethod: PaymentMethod = TIMEOUT_GATEWAY,
    minLatencyMs: Long = 1000L,
    errorType: String = ErrorType.GATEWAY_TIMEOUT,
    customerId: String = "customerId",
    checkoutScenario: String?,
): ILoggingEvent {
    val event = assertLogged(
        level = ERROR,
        message = "Payment gateway timed out for method ${paymentMethod.name}",
        keyValues = mapOf(
            Key.ERROR_TYPE to errorType,
            Key.PAYMENT_METHOD to paymentMethod.name,
        ),
        mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
    )
    val latencyMs = event.keyValuePairs.first { it.key == Key.PAYMENT_LATENCY_MS }.value as Long
    latencyMs shouldBeGreaterThanOrEqualTo minLatencyMs
    event.throwableProxy.shouldNotBeNull()
    return event
}

fun LogCapture.assertOrderShippedLog(
    orderId: String,
    shippingMethod: ShippingMethod = STANDARD,
    previousState: OrderState = PAID,
    customerId: String = "customerId",
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order shipped",
    keyValues = mapOf(
        Key.ORDER_STATE to SHIPPED.name,
        Key.ORDER_PREVIOUS_STATE to previousState.name,
        Key.SHIPPING_METHOD to shippingMethod.name,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertOrderCancelledLog(
    orderId: String,
    previousState: OrderState,
    reason: String,
    customerId: String = "customerId",
    checkoutScenario: String? = null,
): ILoggingEvent = assertLogged(
    level = INFO,
    message = "Order cancelled",
    keyValues = mapOf(
        Key.ORDER_STATE to CANCELLED.name,
        Key.ORDER_PREVIOUS_STATE to previousState.name,
        Key.CANCELLATION_REASON to reason,
    ),
    mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
)

fun LogCapture.assertInvalidStateTransitionLog(
    orderId: String,
    state: OrderState,
    attemptedAction: String,
    errorType: String = ErrorType.INVALID_STATE_TRANSITION,
    customerId: String = "customerId",
    checkoutScenario: String?,
): ILoggingEvent {
    val event = assertLogged(
        level = WARN,
        message = "Cannot $attemptedAction order in ${state.name} state",
        keyValues = mapOf(
            Key.ERROR_TYPE to errorType,
            Key.ORDER_ATTEMPTED_ACTION to attemptedAction,
        ),
        mdc = expectedOrderMdc(orderId, customerId, checkoutScenario),
    )
    event.throwableProxy shouldBe null
    return event
}

fun LogCapture.assertOrderNotFoundLog(orderId: String, checkoutScenario: String?): ILoggingEvent = assertLogged(
    level = WARN,
    message = "Order not found: $orderId",
    mdc = expectedOrderMdc(orderId = orderId, checkoutScenario = checkoutScenario),
)

fun MetricCapture.assertOrdersCreatedIncremented(currency: String = "USD", delta: Long = 1L): PointData =
    assertCounterIncremented(
        name = Metric.ORDERS_CREATED,
        unit = Unit.ORDER,
        delta = delta,
        attributes = mapOf(Key.ORDER_CURRENCY to currency),
    )

fun MetricCapture.assertOrderRepositorySizeGauge(value: Long): PointData = assertGaugeValue(
    name = Metric.ORDER_REPOSITORY_SIZE,
    unit = Unit.ORDER,
    value = value,
)

fun MetricCapture.assertStateTransitionIncremented(
    previousState: OrderState,
    state: OrderState,
    delta: Long = 1L,
): PointData = assertCounterIncremented(
    name = Metric.ORDER_STATE_TRANSITIONS,
    unit = Unit.TRANSITION,
    delta = delta,
    attributes = mapOf(
        Key.ORDER_PREVIOUS_STATE to previousState.name,
        Key.ORDER_STATE to state.name,
    ),
)

fun MetricCapture.assertInventoryReservationIncremented(
    sku: String = "sku",
    outcome: InventoryOutcome,
    delta: Long = 1L,
): PointData = assertCounterIncremented(
    name = Metric.ORDER_INVENTORY_RESERVATIONS,
    unit = Unit.RESERVATION,
    delta = delta,
    attributes = mapOf(
        Key.INVENTORY_SKU to sku,
        Key.INVENTORY_OUTCOME to outcome.name,
    ),
)

fun MetricCapture.assertPaymentDurationRecorded(
    method: PaymentMethod = CREDIT_CARD,
    outcome: PaymentOutcome = SUCCESS,
    count: Long = 1L,
    sum: Double? = 50.0,
): HistogramPointData = assertHistogramRecorded(
    name = Metric.ORDER_PAYMENT_DURATION,
    unit = Unit.MILLISECONDS,
    count = count,
    sum = sum,
    attributes = mapOf(
        Key.PAYMENT_METHOD to method.name,
        Key.PAYMENT_OUTCOME to outcome.name,
    ),
)

fun MetricCapture.assertPaymentTimeoutDurationRecorded(
    method: PaymentMethod = TIMEOUT_GATEWAY,
    minSum: Double = 1000.0,
    count: Long = 1L,
): HistogramPointData {
    val point = assertHistogramRecorded(
        name = Metric.ORDER_PAYMENT_DURATION,
        unit = Unit.MILLISECONDS,
        count = count,
        attributes = mapOf(
            Key.PAYMENT_METHOD to method.name,
            Key.PAYMENT_OUTCOME to TIMEOUT.name,
        ),
    )
    point.sum shouldBeGreaterThanOrEqualTo minSum
    return point
}

fun MetricCapture.assertOrderErrorIncremented(errorType: String, delta: Long = 1L): PointData =
    assertCounterIncremented(
        name = Metric.ORDER_ERRORS,
        unit = Unit.ERROR,
        delta = delta,
        attributes = mapOf(Key.ERROR_TYPE to errorType),
    )
