package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.TrafficMetrics
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.Key
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.Metric
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.Units
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
class OtelTrafficMetrics(openTelemetry: OpenTelemetry) : TrafficMetrics {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
    private val gaugeHandles = CopyOnWriteArrayList<AutoCloseable>()

    private val scenariosExecutedCounter = meter.counterBuilder(Metric.TRAFFIC_SCENARIOS_EXECUTED)
        .setUnit(Units.SCENARIO)
        .build()

    private val scenarioDurationHistogram = meter.histogramBuilder(Metric.TRAFFIC_SCENARIO_DURATION)
        .setUnit(Units.MILLISECONDS)
        .setExplicitBucketBoundariesAdvice(SCENARIO_DURATION_BUCKETS)
        .build()

    override fun recordScenarioExecuted(scenario: CheckoutScenario, finalState: String) {
        val attributes = Attributes.of(
            stringKey(Key.CHECKOUT_SCENARIO),
            scenario.name,
            stringKey(Key.ORDER_STATE),
            finalState,
        )
        scenariosExecutedCounter.add(1L, attributes)
    }

    override fun recordScenarioDuration(scenario: CheckoutScenario, durationMs: Double) {
        val attributes = Attributes.of(
            stringKey(Key.CHECKOUT_SCENARIO),
            scenario.name,
        )
        scenarioDurationHistogram.record(durationMs, attributes)
    }

    override fun registerWorkerGauges(ordersPerSecond: () -> Double, activeWorkers: () -> Long) {
        closeGauges()
        val targetRateGauge = meter.gaugeBuilder(Metric.TRAFFIC_TARGET_RATE)
            .setUnit(Units.ORDER_PER_SECOND)
            .buildWithCallback { measurement ->
                measurement.record(ordersPerSecond())
            }
        val activeWorkersGauge = meter.gaugeBuilder(Metric.TRAFFIC_WORKERS_ACTIVE)
            .ofLongs()
            .setUnit(Units.WORKER)
            .buildWithCallback { measurement ->
                measurement.record(activeWorkers())
            }
        gaugeHandles.addAll(listOf(targetRateGauge, activeWorkersGauge))
    }

    @PreDestroy
    fun closeGauges() {
        val handles = gaugeHandles.toList()
        gaugeHandles.clear()
        handles.forEach(AutoCloseable::close)
    }

    private companion object {
        const val INSTRUMENTATION_SCOPE = "com.observeit.trafficgenerator.traffic"
        val SCENARIO_DURATION_BUCKETS = listOf(
            50.0,
            100.0,
            250.0,
            500.0,
            1000.0,
            1500.0,
            2500.0,
            5000.0,
            10000.0,
        )
    }
}
