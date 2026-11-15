package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.domain.InvalidOrderStateTransitionException
import com.observeit.observedapp.order.domain.InventoryOutcome
import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderNotFoundException
import com.observeit.observedapp.order.domain.OrderReservationSpan
import com.observeit.observedapp.order.domain.OrderState
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderTelemetry.ErrorType
import com.observeit.observedapp.order.domain.OrderTelemetry.Key
import com.observeit.observedapp.order.domain.OrderTracer
import com.observeit.observedapp.order.domain.OutOfStockException
import com.observeit.observedapp.order.domain.PaymentDeclinedException
import com.observeit.observedapp.order.domain.PaymentGatewayTimeoutException
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentOutcome.DECLINED
import com.observeit.observedapp.order.domain.PaymentOutcome.SUCCESS
import com.observeit.observedapp.order.domain.PaymentOutcome.TIMEOUT
import com.observeit.observedapp.order.domain.ShippingMethod
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.api.trace.SpanKind.INTERNAL
import io.opentelemetry.api.trace.StatusCode.ERROR
import org.springframework.stereotype.Component

@Component
class OtelOrderTracer(openTelemetry: OpenTelemetry) : OrderTracer {

    private val tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)

    override fun traceCreateOrder(block: () -> Order): Order = inSpan(SpanName.ORDER_CREATE, INTERNAL) { span ->
        val order = block()
        recordOrderSummary(span, order)
        order
    }

    override fun traceGetOrder(orderId: String, block: () -> Order): Order =
        inOrderSpan(SpanName.ORDER_GET, orderId) { span ->
            val order = block()
            recordOrderSummary(span, order)
            order
        }

    override fun traceReserveOrder(orderId: String, block: (OrderReservationSpan) -> Order): Order =
        inOrderSpan(SpanName.ORDER_RESERVE, orderId) { span ->
            val reservationSpan = object : OrderReservationSpan {
                override fun recordInventoryReservation(
                    sku: String,
                    requestedQuantity: Long,
                    availableQuantity: Long,
                    outcome: InventoryOutcome,
                ) {
                    val eventAttributes = Attributes.of(
                        stringKey(Key.INVENTORY_SKU),
                        sku,
                        longKey(Key.INVENTORY_REQUESTED_QUANTITY),
                        requestedQuantity,
                        longKey(Key.INVENTORY_AVAILABLE_QUANTITY),
                        availableQuantity,
                        stringKey(Key.INVENTORY_OUTCOME),
                        outcome.name,
                    )
                    span.addEvent(EventName.INVENTORY_RESERVATION, eventAttributes)
                }

                override fun recordStateTransition(previousState: OrderState, state: OrderState) {
                    recordTransition(span, previousState, state)
                }
            }
            try {
                val order = block(reservationSpan)
                span.setAttribute(Key.CUSTOMER_ID, order.customerId)
                order
            } catch (exception: OutOfStockException) {
                span.setAttribute(Key.ERROR_TYPE, ErrorType.OUT_OF_STOCK)
                span.setAttribute(Key.INVENTORY_SKU, exception.sku)
                throw exception
            }
        }

    override fun tracePayOrder(
        orderId: String,
        paymentMethod: PaymentMethod,
        latencyMs: Long,
        block: () -> Order,
    ): Order = inOrderSpan(SpanName.ORDER_PAY, orderId) { span ->
        span.setAttribute(Key.PAYMENT_METHOD, paymentMethod.name)
        inPaymentSpan(
            span = span,
            latencyMs = latencyMs,
            onSuccess = { order ->
                span.setAttribute(Key.CUSTOMER_ID, order.customerId)
                recordTransition(span, RESERVED, order.state)
            },
            onFailureTransition = { recordTransition(span, RESERVED, FAILED) },
            block = block,
        )
    }

    override fun tracePaymentExecution(
        order: Order,
        paymentMethod: PaymentMethod,
        latencyMs: Long,
        block: () -> Unit,
    ) {
        inSpan(SpanName.PAYMENT_PROCESS, CLIENT) { span ->
            span.setAttribute(Key.ORDER_ID, order.id)
            span.setAttribute(Key.CUSTOMER_ID, order.customerId)
            span.setAttribute(Key.ORDER_TOTAL_AMOUNT, order.totalAmount.toDouble())
            span.setAttribute(Key.ORDER_CURRENCY, order.currency.currencyCode)
            span.setAttribute(Key.PAYMENT_METHOD, paymentMethod.name)
            inPaymentSpan(span = span, latencyMs = latencyMs, block = block)
        }
    }

    override fun traceShipOrder(orderId: String, shippingMethod: ShippingMethod, block: () -> Order): Order =
        inOrderSpan(SpanName.ORDER_SHIP, orderId) { span ->
            span.setAttribute(Key.SHIPPING_METHOD, shippingMethod.name)
            val order = block()
            span.setAttribute(Key.CUSTOMER_ID, order.customerId)
            recordTransition(span, PAID, order.state)
            order
        }

    override fun traceCancelOrder(
        orderId: String,
        reason: String,
        block: (recordPreviousState: (OrderState) -> Unit) -> Order,
    ): Order = inOrderSpan(SpanName.ORDER_CANCEL, orderId) { span ->
        span.setAttribute(Key.CANCELLATION_REASON, reason)
        var previousState: OrderState? = null
        val order = block { state -> previousState = state }
        span.setAttribute(Key.CUSTOMER_ID, order.customerId)
        previousState?.let { prev -> recordTransition(span, prev, order.state) }
        order
    }

    private inline fun <T> inOrderSpan(name: String, orderId: String, block: (Span) -> T): T =
        inSpan(name, INTERNAL) { span ->
            span.setAttribute(Key.ORDER_ID, orderId)
            try {
                block(span)
            } catch (exception: OrderNotFoundException) {
                span.setAttribute(Key.ERROR_TYPE, ErrorType.ORDER_NOT_FOUND)
                throw exception
            } catch (exception: InvalidOrderStateTransitionException) {
                span.setAttribute(Key.ERROR_TYPE, ErrorType.INVALID_STATE_TRANSITION)
                span.setAttribute(Key.ORDER_ATTEMPTED_ACTION, exception.attemptedAction)
                throw exception
            }
        }

    private inline fun <T> inPaymentSpan(
        span: Span,
        latencyMs: Long,
        onSuccess: (T) -> Unit = {},
        onFailureTransition: () -> Unit = {},
        block: () -> T,
    ): T = try {
        val result = block()
        span.setAttribute(Key.PAYMENT_OUTCOME, SUCCESS.name)
        span.setAttribute(Key.PAYMENT_LATENCY_MS, latencyMs)
        onSuccess(result)
        result
    } catch (exception: PaymentDeclinedException) {
        span.setAttribute(Key.PAYMENT_OUTCOME, DECLINED.name)
        span.setAttribute(Key.PAYMENT_LATENCY_MS, exception.latencyMs)
        span.setAttribute(Key.ERROR_TYPE, ErrorType.PAYMENT_DECLINED)
        onFailureTransition()
        throw exception
    } catch (exception: PaymentGatewayTimeoutException) {
        span.setAttribute(Key.PAYMENT_OUTCOME, TIMEOUT.name)
        span.setAttribute(Key.PAYMENT_LATENCY_MS, exception.latencyMs)
        span.setAttribute(Key.ERROR_TYPE, ErrorType.GATEWAY_TIMEOUT)
        onFailureTransition()
        span.setStatus(ERROR, exception.message ?: GATEWAY_TIMEOUT_STATUS_DESCRIPTION)
        span.recordException(exception)
        throw exception
    }

    private inline fun <T> inSpan(name: String, kind: SpanKind, block: (Span) -> T): T {
        val parentSpan = Span.current()
        val span = tracer.spanBuilder(name)
            .setSpanKind(kind)
            .startSpan()
        CorrelationContext.getCheckoutScenario()?.let { scenario ->
            span.setAttribute(Key.CHECKOUT_SCENARIO, scenario)
        }
        return try {
            span.makeCurrent().use {
                block(span)
            }
        } finally {
            if (parentSpan.spanContext.isValid) {
                CorrelationContext.getCustomerId()?.let { customerId ->
                    parentSpan.setAttribute(Key.CUSTOMER_ID, customerId)
                }
            }
            span.end()
        }
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
    }

    private companion object {
        const val INSTRUMENTATION_SCOPE = "com.observeit.observedapp.order"
        const val GATEWAY_TIMEOUT_STATUS_DESCRIPTION = "Payment gateway timed out"
    }
}

private const val ORDER_STATE_TRANSITIONED_EVENT = "order.state.transitioned"

private fun recordOrderSummary(span: Span, order: Order) {
    span.setAttribute(Key.ORDER_ID, order.id)
    span.setAttribute(Key.CUSTOMER_ID, order.customerId)
    span.setAttribute(Key.ORDER_STATE, order.state.name)
    span.setAttribute(Key.ORDER_TOTAL_AMOUNT, order.totalAmount.toDouble())
    span.setAttribute(Key.ORDER_CURRENCY, order.currency.currencyCode)
    span.setAttribute(Key.ORDER_ITEM_COUNT, order.itemCount)
}

private fun recordTransition(span: Span, previousState: OrderState, state: OrderState) {
    span.setAttribute(Key.ORDER_PREVIOUS_STATE, previousState.name)
    span.setAttribute(Key.ORDER_STATE, state.name)
    span.addEvent(
        ORDER_STATE_TRANSITIONED_EVENT,
        Attributes.of(
            stringKey(Key.ORDER_PREVIOUS_STATE),
            previousState.name,
            stringKey(Key.ORDER_STATE),
            state.name,
        ),
    )
}
