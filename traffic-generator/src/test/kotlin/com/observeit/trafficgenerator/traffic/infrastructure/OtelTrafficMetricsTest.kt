package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.test.MetricCapture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OtelTrafficMetricsTest {

    @JvmField
    @RegisterExtension
    val metricCapture = MetricCapture()

    private val metrics = OtelTrafficMetrics(metricCapture.openTelemetry)

    @Test
    fun `records traffic scenarios executed counter with scenario and order state attributes`() {
        // when
        metrics.recordScenarioExecuted(scenario = HAPPY_PATH, finalState = "SHIPPED")
        metrics.recordScenarioExecuted(scenario = OUT_OF_STOCK, finalState = "FAILED")

        // then
        metricCapture.assertCounterIncremented(
            name = "traffic.scenarios.executed",
            unit = "{scenario}",
            delta = 1L,
            attributes = mapOf(
                "checkout.scenario" to "HAPPY_PATH",
                "order.state" to "SHIPPED",
            ),
        )
        metricCapture.assertCounterIncremented(
            name = "traffic.scenarios.executed",
            unit = "{scenario}",
            delta = 1L,
            attributes = mapOf(
                "checkout.scenario" to "OUT_OF_STOCK",
                "order.state" to "FAILED",
            ),
        )
    }

    @Test
    fun `records scenario duration histogram with explicit bucket boundaries and scenario attribute`() {
        // when
        metrics.recordScenarioDuration(scenario = HAPPY_PATH, durationMs = 250.0)

        // then
        val point = metricCapture.assertHistogramRecorded(
            name = "traffic.scenario.duration",
            unit = "ms",
            count = 1L,
            sum = 250.0,
            attributes = mapOf("checkout.scenario" to "HAPPY_PATH"),
        )
        point.boundaries shouldBe listOf(50.0, 100.0, 250.0, 500.0, 1000.0, 1500.0, 2500.0, 5000.0, 10000.0)
    }

    @Test
    fun `reads initial target rate and active workers gauges from registered callbacks`() {
        // given
        val configuredRate = 1.0
        val activeWorkerCount = 0L

        // when
        metrics.registerWorkerGauges(
            ordersPerSecond = { configuredRate },
            activeWorkers = { activeWorkerCount },
        )

        // then
        metricCapture.assertGaugeValue(
            name = "traffic.target_rate",
            unit = "{order}/s",
            value = 1.0,
        )
        metricCapture.assertGaugeValue(
            name = "traffic.workers.active",
            unit = "{worker}",
            value = 0L,
        )
    }

    @Test
    fun `reads updated target rate and active workers gauges when callback state changes`() {
        // given
        var configuredRate = 1.0
        var activeWorkerCount = 0L
        metrics.registerWorkerGauges(
            ordersPerSecond = { configuredRate },
            activeWorkers = { activeWorkerCount },
        )
        metricCapture.drainAndReset()

        // when
        configuredRate = 4.5
        activeWorkerCount = 6L

        // then
        metricCapture.assertGaugeValue(
            name = "traffic.target_rate",
            unit = "{order}/s",
            value = 4.5,
        )
        metricCapture.assertGaugeValue(
            name = "traffic.workers.active",
            unit = "{worker}",
            value = 6L,
        )
    }
}
