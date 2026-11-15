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
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentGatewayTimeoutException
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import com.observeit.observedapp.order.domain.PaymentOutcome
import com.observeit.observedapp.order.domain.PaymentOutcome.DECLINED
import com.observeit.observedapp.order.domain.PaymentOutcome.SUCCESS
import com.observeit.observedapp.order.domain.PaymentOutcome.TIMEOUT
import com.observeit.observedapp.order.domain.ShippingMethod
import com.observeit.observedapp.order.domain.ShippingMethod.STANDARD
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.MetricCapture
import com.observeit.observedapp.test.SpanCapture
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.api.trace.SpanKind.INTERNAL
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.StatusCode.UNSET
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData

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
    const val ORDER_NOT_FOUND = "ORDER_NOT_FOUND"
    const val OUT_OF_STOCK = "OUT_OF_STOCK"
    const val PAYMENT_DECLINED = "PAYMENT_DECLINED"
    const val GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT"
    const val INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION"
}

private object SpanName {
    const val ORDER_CREATE = "order.create"
    const val ORDER_GET = "order.get"
    const val ORDER_RESERVE = "order.reserve"
    const val ORDER_PAY = "order.pay"
    const val PAYMENT_PROCESS = "payment.process"
    const val ORDER_SHIP = "order.ship"
    const val ORDER_CANCEL = "order.cancel"
}

private object EventName {
    const val INVENTORY_RESERVATION = "inventory.reservation"
    const val ORDER_STATE_TRANSITIONED = "order.state.transitioned"
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

private fun expectedOrderSpanAttributes(
    orderId: String,
    customerId: String? = null,
    checkoutScenario: String? = null,
    extra: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = buildMap {
    put(Key.ORDER_ID, orderId)
    if (customerId != null) {
        put(Key.CUSTOMER_ID, customerId)
    }
    if (checkoutScenario != null) {
        put(Key.CHECKOUT_SCENARIO, checkoutScenario)
    }
    putAll(extra)
}

fun SpanCapture.assertOrderCreatedSpan(
    orderId: String,
    customerId: String = "customerId",
    totalAmount: Double = 10.0,
    currency: String = "USD",
    itemCount: Long = 1L,
    checkoutScenario: String? = null,
): SpanData {
    val span = assertSpanRecorded(
        name = SpanName.ORDER_CREATE,
        kind = INTERNAL,
        status = UNSET,
        attributes = expectedOrderSpanAttributes(
            orderId = orderId,
            customerId = customerId,
            checkoutScenario = checkoutScenario,
            extra = mapOf(
                Key.ORDER_STATE to CREATED.name,
                Key.ORDER_TOTAL_AMOUNT to totalAmount,
                Key.ORDER_CURRENCY to currency,
                Key.ORDER_ITEM_COUNT to itemCount,
            ),
        ),
    )
    assertNoExceptionRecorded(SpanName.ORDER_CREATE)
    return span
}

fun SpanCapture.assertOrderRetrievedSpan(
    orderId: String,
    customerId: String = "customerId",
    state: OrderState = CREATED,
    totalAmount: Double = 10.0,
    currency: String = "USD",
    itemCount: Long = 1L,
    checkoutScenario: String? = null,
): SpanData {
    val span = assertSpanRecorded(
        name = SpanName.ORDER_GET,
        kind = INTERNAL,
        status = UNSET,
        attributes = expectedOrderSpanAttributes(
            orderId = orderId,
            customerId = customerId,
            checkoutScenario = checkoutScenario,
            extra = mapOf(
                Key.ORDER_STATE to state.name,
                Key.ORDER_TOTAL_AMOUNT to totalAmount,
                Key.ORDER_CURRENCY to currency,
                Key.ORDER_ITEM_COUNT to itemCount,
            ),
        ),
    )
    assertNoExceptionRecorded(SpanName.ORDER_GET)
    return span
}

fun SpanCapture.assertOrderNotFoundSpan(
    spanName: String,
    orderId: String,
    checkoutScenario: String? = null,
): SpanData {
    val span = assertSpanRecorded(
        name = spanName,
        kind = INTERNAL,
        status = UNSET,
        attributes = expectedOrderSpanAttributes(
            orderId = orderId,
            checkoutScenario = checkoutScenario,
            extra = mapOf(Key.ERROR_TYPE to ErrorType.ORDER_NOT_FOUND),
        ),
    )
    assertNoExceptionRecorded(spanName)
    return span
}

fun SpanCapture.assertOrderReservedSpan(
    orderId: String,
    customerId: String = "customerId",
    previousState: OrderState = CREATED,
    state: OrderState = RESERVED,
    checkoutScenario: String? = null,
): SpanData = assertTransitionSpan(
    spanName = SpanName.ORDER_RESERVE,
    previousState = previousState,
    state = state,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
    ),
)

fun SpanCapture.assertInventoryReservationSpanEvent(
    sku: String = "sku",
    requestedQuantity: Long = 1L,
    availableQuantity: Long = 100L,
    outcome: InventoryOutcome = InventoryOutcome.RESERVED,
): EventData = assertSpanEventRecorded(
    spanName = SpanName.ORDER_RESERVE,
    eventName = EventName.INVENTORY_RESERVATION,
    attributes = mapOf(
        Key.INVENTORY_SKU to sku,
        Key.INVENTORY_REQUESTED_QUANTITY to requestedQuantity,
        Key.INVENTORY_AVAILABLE_QUANTITY to availableQuantity,
        Key.INVENTORY_OUTCOME to outcome.name,
    ),
)

fun SpanCapture.assertOrderOutOfStockSpan(
    orderId: String,
    sku: String,
    customerId: String = "customerId",
    previousState: OrderState = CREATED,
    checkoutScenario: String? = null,
): SpanData = assertTransitionSpan(
    spanName = SpanName.ORDER_RESERVE,
    previousState = previousState,
    state = FAILED,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(
            Key.ERROR_TYPE to ErrorType.OUT_OF_STOCK,
            Key.INVENTORY_SKU to sku,
        ),
    ),
)

fun SpanCapture.assertInvalidStateTransitionSpan(
    spanName: String,
    orderId: String,
    attemptedAction: String,
    checkoutScenario: String? = null,
): SpanData {
    val span = assertSpanRecorded(
        name = spanName,
        kind = INTERNAL,
        status = UNSET,
        attributes = expectedOrderSpanAttributes(
            orderId = orderId,
            checkoutScenario = checkoutScenario,
            extra = mapOf(
                Key.ERROR_TYPE to ErrorType.INVALID_STATE_TRANSITION,
                Key.ORDER_ATTEMPTED_ACTION to attemptedAction,
            ),
        ),
    )
    assertNoExceptionRecorded(spanName)
    return span
}

fun SpanCapture.assertOrderPaidSpan(
    orderId: String,
    customerId: String = "customerId",
    paymentMethod: PaymentMethod = CREDIT_CARD,
    paymentLatencyMs: Long = 50L,
    previousState: OrderState = RESERVED,
    state: OrderState = PAID,
    checkoutScenario: String? = null,
): SpanData = assertTransitionSpan(
    spanName = SpanName.ORDER_PAY,
    previousState = previousState,
    state = state,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(
            Key.PAYMENT_METHOD to paymentMethod.name,
            Key.PAYMENT_OUTCOME to SUCCESS.name,
            Key.PAYMENT_LATENCY_MS to paymentLatencyMs,
        ),
    ),
)

fun SpanCapture.assertPaymentProcessSpan(
    orderId: String,
    customerId: String = "customerId",
    totalAmount: Double = 10.0,
    currency: String = "USD",
    paymentMethod: PaymentMethod = CREDIT_CARD,
    paymentLatencyMs: Long = 50L,
    checkoutScenario: String? = null,
    traceId: String? = null,
    parentSpanId: String? = null,
): SpanData = assertPaymentProcessSpanBase(
    status = UNSET,
    traceId = traceId,
    parentSpanId = parentSpanId,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(
            Key.ORDER_TOTAL_AMOUNT to totalAmount,
            Key.ORDER_CURRENCY to currency,
            Key.PAYMENT_METHOD to paymentMethod.name,
            Key.PAYMENT_OUTCOME to SUCCESS.name,
            Key.PAYMENT_LATENCY_MS to paymentLatencyMs,
        ),
    ),
)

fun SpanCapture.assertPaymentDeclinedSpan(
    orderId: String,
    customerId: String = "customerId",
    paymentMethod: PaymentMethod = DECLINED_CARD,
    paymentLatencyMs: Long = 50L,
    previousState: OrderState = RESERVED,
    checkoutScenario: String? = null,
): SpanData = assertTransitionSpan(
    spanName = SpanName.ORDER_PAY,
    previousState = previousState,
    state = FAILED,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(
            Key.PAYMENT_METHOD to paymentMethod.name,
            Key.PAYMENT_OUTCOME to DECLINED.name,
            Key.PAYMENT_LATENCY_MS to paymentLatencyMs,
            Key.ERROR_TYPE to ErrorType.PAYMENT_DECLINED,
        ),
    ),
)

fun SpanCapture.assertPaymentDeclinedProcessSpan(
    orderId: String,
    customerId: String = "customerId",
    totalAmount: Double = 10.0,
    currency: String = "USD",
    paymentMethod: PaymentMethod = DECLINED_CARD,
    paymentLatencyMs: Long = 50L,
    checkoutScenario: String? = null,
    traceId: String? = null,
    parentSpanId: String? = null,
): SpanData = assertPaymentProcessSpanBase(
    status = UNSET,
    traceId = traceId,
    parentSpanId = parentSpanId,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(
            Key.ORDER_TOTAL_AMOUNT to totalAmount,
            Key.ORDER_CURRENCY to currency,
            Key.PAYMENT_METHOD to paymentMethod.name,
            Key.PAYMENT_OUTCOME to DECLINED.name,
            Key.PAYMENT_LATENCY_MS to paymentLatencyMs,
            Key.ERROR_TYPE to ErrorType.PAYMENT_DECLINED,
        ),
    ),
)

fun SpanCapture.assertPaymentTimeoutSpan(
    orderId: String,
    customerId: String = "customerId",
    paymentMethod: PaymentMethod = TIMEOUT_GATEWAY,
    minLatencyMs: Long = 1000L,
    previousState: OrderState = RESERVED,
    checkoutScenario: String? = null,
): SpanData {
    val span = assertSpanRecorded(
        name = SpanName.ORDER_PAY,
        kind = INTERNAL,
        status = StatusCode.ERROR,
        attributes = expectedOrderSpanAttributes(
            orderId = orderId,
            customerId = customerId,
            checkoutScenario = checkoutScenario,
            extra = mapOf(
                Key.ORDER_PREVIOUS_STATE to previousState.name,
                Key.ORDER_STATE to FAILED.name,
                Key.PAYMENT_METHOD to paymentMethod.name,
                Key.PAYMENT_OUTCOME to TIMEOUT.name,
                Key.ERROR_TYPE to ErrorType.GATEWAY_TIMEOUT,
            ),
        ),
    )
    span.attributes.get(longKey(Key.PAYMENT_LATENCY_MS)).shouldNotBeNull().shouldBeGreaterThanOrEqualTo(minLatencyMs)
    assertSpanEventRecorded(
        spanName = SpanName.ORDER_PAY,
        eventName = EventName.ORDER_STATE_TRANSITIONED,
        attributes = mapOf(
            Key.ORDER_PREVIOUS_STATE to previousState.name,
            Key.ORDER_STATE to FAILED.name,
        ),
    )
    assertExceptionRecorded(SpanName.ORDER_PAY, exceptionType = PaymentGatewayTimeoutException::class)
    return span
}

fun SpanCapture.assertPaymentTimeoutProcessSpan(
    orderId: String,
    customerId: String = "customerId",
    totalAmount: Double = 10.0,
    currency: String = "USD",
    paymentMethod: PaymentMethod = TIMEOUT_GATEWAY,
    minLatencyMs: Long = 1000L,
    checkoutScenario: String? = null,
    traceId: String? = null,
    parentSpanId: String? = null,
): SpanData {
    val span = assertPaymentProcessSpanBase(
        status = StatusCode.ERROR,
        traceId = traceId,
        parentSpanId = parentSpanId,
        attributes = expectedOrderSpanAttributes(
            orderId = orderId,
            customerId = customerId,
            checkoutScenario = checkoutScenario,
            extra = mapOf(
                Key.ORDER_TOTAL_AMOUNT to totalAmount,
                Key.ORDER_CURRENCY to currency,
                Key.PAYMENT_METHOD to paymentMethod.name,
                Key.PAYMENT_OUTCOME to TIMEOUT.name,
                Key.ERROR_TYPE to ErrorType.GATEWAY_TIMEOUT,
            ),
        ),
    )
    span.attributes.get(longKey(Key.PAYMENT_LATENCY_MS)).shouldNotBeNull().shouldBeGreaterThanOrEqualTo(minLatencyMs)
    assertExceptionRecorded(SpanName.PAYMENT_PROCESS, exceptionType = PaymentGatewayTimeoutException::class)
    return span
}

private fun SpanCapture.assertPaymentProcessSpanBase(
    status: StatusCode,
    traceId: String?,
    parentSpanId: String?,
    attributes: Map<String, Any?>,
): SpanData {
    val span = assertSpanRecorded(
        name = SpanName.PAYMENT_PROCESS,
        kind = CLIENT,
        status = status,
        traceId = traceId,
        parentSpanId = parentSpanId,
        attributes = attributes,
    )
    if (status == UNSET) {
        assertNoExceptionRecorded(SpanName.PAYMENT_PROCESS)
    }
    return span
}

fun SpanCapture.assertOrderShippedSpan(
    orderId: String,
    customerId: String = "customerId",
    shippingMethod: ShippingMethod = STANDARD,
    previousState: OrderState = PAID,
    state: OrderState = SHIPPED,
    checkoutScenario: String? = null,
): SpanData = assertTransitionSpan(
    spanName = SpanName.ORDER_SHIP,
    previousState = previousState,
    state = state,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(Key.SHIPPING_METHOD to shippingMethod.name),
    ),
)

fun SpanCapture.assertOrderCancelledSpan(
    orderId: String,
    customerId: String = "customerId",
    reason: String = "reason",
    previousState: OrderState = CREATED,
    state: OrderState = CANCELLED,
    checkoutScenario: String? = null,
): SpanData = assertTransitionSpan(
    spanName = SpanName.ORDER_CANCEL,
    previousState = previousState,
    state = state,
    attributes = expectedOrderSpanAttributes(
        orderId = orderId,
        customerId = customerId,
        checkoutScenario = checkoutScenario,
        extra = mapOf(Key.CANCELLATION_REASON to reason),
    ),
)

private fun SpanCapture.assertTransitionSpan(
    spanName: String,
    previousState: OrderState,
    state: OrderState,
    attributes: Map<String, Any?>,
): SpanData {
    val span = assertSpanRecorded(
        name = spanName,
        kind = INTERNAL,
        status = UNSET,
        attributes = attributes + mapOf(
            Key.ORDER_PREVIOUS_STATE to previousState.name,
            Key.ORDER_STATE to state.name,
        ),
    )
    assertSpanEventRecorded(
        spanName = spanName,
        eventName = EventName.ORDER_STATE_TRANSITIONED,
        attributes = mapOf(
            Key.ORDER_PREVIOUS_STATE to previousState.name,
            Key.ORDER_STATE to state.name,
        ),
    )
    assertNoExceptionRecorded(spanName)
    return span
}
