package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.domain.InventoryOutcome.LOW_STOCK_WARNING
import com.observeit.observedapp.order.domain.InventoryOutcome.OUT_OF_STOCK
import com.observeit.observedapp.order.domain.InventoryOutcome.RESERVED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentOutcome.SUCCESS
import com.observeit.observedapp.test.MetricCapture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Currency

class OtelOrderMetricsTest {

    @JvmField
    @RegisterExtension
    val metricCapture = MetricCapture.isolated()

    private val repository = InMemoryOrderRepository()
    private val metrics = OtelOrderMetrics(metricCapture.openTelemetry, repository)

    @Test
    fun `records order created counter with currency attribute`() {
        // when
        metrics.recordOrderCreated(Currency.getInstance("USD"))

        // then
        metricCapture.assertCounterIncremented(
            name = "orders.created",
            unit = "{order}",
            delta = 1L,
            attributes = mapOf("order.currency" to "USD"),
        )
    }

    @Test
    fun `records order state transitions counter with previous and new state attributes`() {
        // when
        metrics.recordStateTransition(previousState = CREATED, newState = FAILED)

        // then
        metricCapture.assertCounterIncremented(
            name = "order.state.transitions",
            unit = "{transition}",
            delta = 1L,
            attributes = mapOf(
                "order.previous_state" to "CREATED",
                "order.state" to "FAILED",
            ),
        )
    }

    @Test
    fun `records order errors counter with error type attribute`() {
        // when
        metrics.recordError("OUT_OF_STOCK")

        // then
        metricCapture.assertCounterIncremented(
            name = "order.errors",
            unit = "{error}",
            delta = 1L,
            attributes = mapOf("error.type" to "OUT_OF_STOCK"),
        )
    }

    @Test
    fun `records inventory reservations counter with sku and outcome attributes`() {
        // when
        metrics.recordInventoryReservation(sku = "SKU-1", outcome = RESERVED)
        metrics.recordInventoryReservation(sku = "SKU-LOW-STOCK", outcome = LOW_STOCK_WARNING)
        metrics.recordInventoryReservation(sku = "SKU-OUT-OF-STOCK", outcome = OUT_OF_STOCK)

        // then
        metricCapture.assertCounterIncremented(
            name = "order.inventory.reservations",
            unit = "{reservation}",
            delta = 1L,
            attributes = mapOf(
                "inventory.sku" to "SKU-1",
                "inventory.outcome" to "RESERVED",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.inventory.reservations",
            unit = "{reservation}",
            delta = 1L,
            attributes = mapOf(
                "inventory.sku" to "SKU-LOW-STOCK",
                "inventory.outcome" to "LOW_STOCK_WARNING",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "order.inventory.reservations",
            unit = "{reservation}",
            delta = 1L,
            attributes = mapOf(
                "inventory.sku" to "SKU-OUT-OF-STOCK",
                "inventory.outcome" to "OUT_OF_STOCK",
            ),
        )
    }

    @Test
    fun `records payment duration histogram with explicit bucket boundaries and attributes`() {
        // when
        metrics.recordPaymentDuration(
            method = CREDIT_CARD,
            outcome = SUCCESS,
            durationMs = 50.0,
        )

        // then
        val point = metricCapture.assertHistogramRecorded(
            name = "order.payment.duration",
            unit = "ms",
            count = 1L,
            sum = 50.0,
            attributes = mapOf(
                "payment.method" to "CREDIT_CARD",
                "payment.outcome" to "SUCCESS",
            ),
        )
        point.boundaries shouldBe listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0)
    }

    @Test
    fun `reads repository size gauge callback from repository count`() {
        // then
        metricCapture.assertGaugeValue(
            name = "order.repository.size",
            unit = "{order}",
            value = 0L,
        )

        // when
        repository.save(anOrder(id = "order-1"))
        repository.save(anOrder(id = "order-2"))

        // then
        metricCapture.assertGaugeValue(
            name = "order.repository.size",
            unit = "{order}",
            value = 2L,
        )
    }
}
