package com.observeit.observedapp.test

import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.data.MetricDataType.DOUBLE_GAUGE
import io.opentelemetry.sdk.metrics.data.MetricDataType.DOUBLE_SUM
import io.opentelemetry.sdk.metrics.data.MetricDataType.EXPONENTIAL_HISTOGRAM
import io.opentelemetry.sdk.metrics.data.MetricDataType.HISTOGRAM
import io.opentelemetry.sdk.metrics.data.MetricDataType.LONG_GAUGE
import io.opentelemetry.sdk.metrics.data.MetricDataType.LONG_SUM
import io.opentelemetry.sdk.metrics.data.MetricDataType.SUMMARY
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.CopyOnWriteArrayList

class MetricCapture(
    private val metricReader: InMemoryMetricReader = InMemoryMetricReader.createDelta(),
    meterProvider: SdkMeterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build(),
    val openTelemetry: OpenTelemetrySdk =
        OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build(),
) : BeforeEachCallback,
    AfterEachCallback {

    private val collectedMetrics = CopyOnWriteArrayList<MetricData>()

    val meter: Meter
        get() = openTelemetry.getMeter(DEFAULT_INSTRUMENTATION_SCOPE)

    override fun beforeEach(context: ExtensionContext) {
        drainAndReset()
    }

    override fun afterEach(context: ExtensionContext) {
        drainAndReset()
    }

    fun drainAndReset() {
        metricReader.collectAllMetrics()
        collectedMetrics.clear()
    }

    fun assertCounterIncremented(
        name: String,
        unit: String? = null,
        delta: Long = 1L,
        attributes: Map<String, Any?> = emptyMap(),
    ): PointData {
        val metric = findMetric(name, unit)
        val point = findMatchingPoint(name, metric.type, attributes)
        when (point) {
            is LongPointData -> point.value shouldBe delta
            is DoublePointData -> point.value shouldBe delta.toDouble()
            else -> fail("Point for '$name' was not a numeric point data: ${point::class.simpleName}")
        }
        return point
    }

    fun assertCounterIncremented(
        name: String,
        unit: String? = null,
        delta: Double,
        attributes: Map<String, Any?> = emptyMap(),
    ): PointData {
        val metric = findMetric(name, unit)
        val point = findMatchingPoint(name, metric.type, attributes)
        when (point) {
            is DoublePointData -> point.value shouldBe delta
            is LongPointData -> point.value.toDouble() shouldBe delta
            else -> fail("Point for '$name' was not a numeric point data: ${point::class.simpleName}")
        }
        return point
    }

    fun assertHistogramRecorded(
        name: String,
        unit: String? = null,
        count: Long = 1L,
        sum: Double? = null,
        attributes: Map<String, Any?> = emptyMap(),
    ): HistogramPointData {
        val metric = findMetric(name, unit)
        if (metric.type != HISTOGRAM) {
            fail("Metric '$name' has type '${metric.type}', expected HISTOGRAM")
        }
        val point = findMatchingPoint(name, metric.type, attributes) as HistogramPointData
        point.count shouldBe count
        if (sum != null) {
            point.sum shouldBe sum
        }
        return point
    }

    fun assertGaugeValue(
        name: String,
        unit: String? = null,
        value: Long,
        attributes: Map<String, Any?> = emptyMap(),
    ): PointData {
        val metric = findMetric(name, unit)
        val point = findMatchingPoint(name, metric.type, attributes)
        when (point) {
            is LongPointData -> point.value shouldBe value
            is DoublePointData -> point.value shouldBe value.toDouble()
            else -> fail("Point for '$name' was not a gauge point data: ${point::class.simpleName}")
        }
        return point
    }

    fun assertGaugeValue(
        name: String,
        unit: String? = null,
        value: Double,
        attributes: Map<String, Any?> = emptyMap(),
    ): PointData {
        val metric = findMetric(name, unit)
        val point = findMatchingPoint(name, metric.type, attributes)
        when (point) {
            is DoublePointData -> point.value shouldBe value
            is LongPointData -> point.value.toDouble() shouldBe value
            else -> fail("Point for '$name' was not a gauge point data: ${point::class.simpleName}")
        }
        return point
    }

    private fun collectMetrics(): List<MetricData> {
        val newMetrics = metricReader.collectAllMetrics()
        if (newMetrics.isNotEmpty()) {
            collectedMetrics.addAll(newMetrics)
        }
        return collectedMetrics.toList()
    }

    private fun findMetric(name: String, unit: String?): MetricData {
        val metrics = collectMetrics()
        val matching = metrics.filter { it.name == name }
        if (matching.isEmpty()) {
            val available = metrics.map { it.name }.distinct()
            fail("Metric '$name' was not recorded. Available metrics: $available")
        }
        val metric = matching.last()
        if (unit != null) {
            metric.unit shouldBe unit
        }
        return metric
    }

    private fun findMatchingPoint(
        metricName: String,
        metricType: MetricDataType,
        expectedAttributes: Map<String, Any?>,
    ): PointData {
        val metrics = collectMetrics()
        val matchingMetrics = metrics.filter { it.name == metricName }
        val points = matchingMetrics.flatMap { metric ->
            when (metricType) {
                LONG_SUM -> metric.longSumData.points
                DOUBLE_SUM -> metric.doubleSumData.points
                HISTOGRAM -> metric.histogramData.points
                LONG_GAUGE -> metric.longGaugeData.points
                DOUBLE_GAUGE -> metric.doubleGaugeData.points
                SUMMARY, EXPONENTIAL_HISTOGRAM ->
                    fail("Unsupported metric type '$metricType' for metric '$metricName'")
            }
        }
        val matching = points.filter { point ->
            val actualAttributes = point.attributes.asMap().mapKeys { it.key.key }
            actualAttributes == expectedAttributes
        }
        if (matching.isEmpty()) {
            val recorded = points.map { it.attributes.asMap().mapKeys { entry -> entry.key.key } }
            fail("No point for metric '$metricName' matched attributes $expectedAttributes. Recorded: $recorded")
        }
        return matching.last()
    }

    private companion object {
        const val DEFAULT_INSTRUMENTATION_SCOPE = "observe-it"
    }
}
