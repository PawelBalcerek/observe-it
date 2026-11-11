package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.domain.InventoryOutcome
import com.observeit.observedapp.order.domain.OrderMetrics
import com.observeit.observedapp.order.domain.OrderRepository
import com.observeit.observedapp.order.domain.OrderState
import com.observeit.observedapp.order.domain.OrderTelemetry.Key
import com.observeit.observedapp.order.domain.OrderTelemetry.Metric
import com.observeit.observedapp.order.domain.OrderTelemetry.Units
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentOutcome
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import org.springframework.stereotype.Component
import java.util.Currency

@Component
class OtelOrderMetrics(openTelemetry: OpenTelemetry, orderRepository: OrderRepository) : OrderMetrics {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val ordersCreatedCounter = meter.counterBuilder(Metric.ORDERS_CREATED)
        .setUnit(Units.ORDER)
        .build()

    private val stateTransitionsCounter = meter.counterBuilder(Metric.ORDER_STATE_TRANSITIONS)
        .setUnit(Units.TRANSITION)
        .build()

    private val errorsCounter = meter.counterBuilder(Metric.ORDER_ERRORS)
        .setUnit(Units.ERROR)
        .build()

    private val inventoryReservationsCounter = meter.counterBuilder(Metric.ORDER_INVENTORY_RESERVATIONS)
        .setUnit(Units.RESERVATION)
        .build()

    private val paymentDurationHistogram = meter.histogramBuilder(Metric.ORDER_PAYMENT_DURATION)
        .setUnit(Units.MILLISECONDS)
        .setExplicitBucketBoundariesAdvice(PAYMENT_DURATION_BUCKETS)
        .build()

    init {
        meter.gaugeBuilder(Metric.ORDER_REPOSITORY_SIZE)
            .ofLongs()
            .setUnit(Units.ORDER)
            .buildWithCallback { measurement ->
                measurement.record(orderRepository.count().toLong())
            }
    }

    override fun recordOrderCreated(currency: Currency) {
        val attributes = Attributes.of(
            stringKey(Key.ORDER_CURRENCY),
            currency.currencyCode,
        )
        ordersCreatedCounter.add(1L, attributes)
    }

    override fun recordStateTransition(previousState: OrderState, newState: OrderState) {
        val attributes = Attributes.of(
            stringKey(Key.ORDER_PREVIOUS_STATE),
            previousState.name,
            stringKey(Key.ORDER_STATE),
            newState.name,
        )
        stateTransitionsCounter.add(1L, attributes)
    }

    override fun recordError(errorType: String) {
        val attributes = Attributes.of(
            stringKey(Key.ERROR_TYPE),
            errorType,
        )
        errorsCounter.add(1L, attributes)
    }

    override fun recordInventoryReservation(sku: String, outcome: InventoryOutcome) {
        val attributes = Attributes.of(
            stringKey(Key.INVENTORY_SKU),
            sku,
            stringKey(Key.INVENTORY_OUTCOME),
            outcome.name,
        )
        inventoryReservationsCounter.add(1L, attributes)
    }

    override fun recordPaymentDuration(method: PaymentMethod, outcome: PaymentOutcome, durationMs: Double) {
        val attributes = Attributes.of(
            stringKey(Key.PAYMENT_METHOD),
            method.name,
            stringKey(Key.PAYMENT_OUTCOME),
            outcome.name,
        )
        paymentDurationHistogram.record(durationMs, attributes)
    }

    private companion object {
        const val INSTRUMENTATION_SCOPE = "com.observeit.observedapp.order"
        val PAYMENT_DURATION_BUCKETS = listOf(
            10.0,
            25.0,
            50.0,
            100.0,
            250.0,
            500.0,
            1000.0,
            2500.0,
            5000.0,
        )
    }
}
