package com.observeit.observedapp.test

import io.kotest.assertions.fail
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass

class SpanCapture :
    BeforeEachCallback,
    AfterEachCallback {

    private val spanExporter: InMemorySpanExporter = InMemorySpanExporter.create()
    val tracerProvider: SdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
    val propagators: ContextPropagators =
        ContextPropagators.create(W3CTraceContextPropagator.getInstance())
    val openTelemetry: OpenTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(propagators)
            .build()

    override fun beforeEach(context: ExtensionContext) {
        reset()
    }

    override fun afterEach(context: ExtensionContext) {
        reset()
    }

    fun finishedSpans(): List<SpanData> = spanExporter.finishedSpanItems

    fun reset() {
        spanExporter.reset()
    }

    fun assertSpanRecorded(
        name: String,
        kind: SpanKind? = null,
        status: StatusCode? = null,
        attributes: Map<String, Any?> = emptyMap(),
        traceId: String? = null,
        parentSpanId: String? = null,
    ): SpanData {
        val spans = finishedSpans()
        val matching =
            spans.filter { span ->
                span.name == name &&
                    (kind == null || span.kind == kind) &&
                    (status == null || span.status.statusCode == status) &&
                    (traceId == null || span.traceId == traceId) &&
                    (parentSpanId == null || span.parentSpanId == parentSpanId) &&
                    span.attributes.containsAll(attributes)
            }
        if (matching.isEmpty()) {
            val available =
                spans.map {
                    "${it.name}(kind=${it.kind}, status=${it.status.statusCode}, " +
                        "attrs=${it.attributes.toStringKeyMap()})"
                }
            fail(
                "No span recorded matching name='$name', kind=$kind, status=$status, " +
                    "attributes=$attributes, traceId=$traceId, parentSpanId=$parentSpanId. " +
                    "Recorded spans: $available",
            )
        }
        return matching.last()
    }

    fun assertSpanEventRecorded(
        spanName: String,
        eventName: String,
        attributes: Map<String, Any?> = emptyMap(),
    ): EventData {
        val spans = spansByName(spanName)
        val matchingEvents =
            spans.flatMap { it.events }.filter { event ->
                event.name == eventName && event.attributes.containsAll(attributes)
            }
        if (matchingEvents.isEmpty()) {
            fail(
                "No event '$eventName' with attributes $attributes recorded on span '$spanName'. " +
                    "Recorded events: ${spans.formatEvents()}",
            )
        }
        return matchingEvents.last()
    }

    fun assertExceptionRecorded(
        spanName: String,
        exceptionType: KClass<out Throwable>? = null,
        message: String? = null,
    ): EventData {
        val spans = spansByName(spanName)
        val matchingEvents =
            spans.flatMap { it.events }.filter { event ->
                if (event.name != EXCEPTION_EVENT_NAME) {
                    return@filter false
                }
                val attrs = event.attributes.toStringKeyMap()
                val typeMatches = exceptionType == null || attrs[EXCEPTION_TYPE_KEY] == exceptionType.java.name
                val messageMatches = message == null || attrs[EXCEPTION_MESSAGE_KEY] == message
                typeMatches && messageMatches
            }
        if (matchingEvents.isEmpty()) {
            fail(
                "No exception event recorded on span '$spanName' matching " +
                    "type=${exceptionType?.java?.name}, message=$message. Recorded events: ${spans.formatEvents()}",
            )
        }
        return matchingEvents.last()
    }

    fun assertNoExceptionRecorded(spanName: String) {
        val spans = spansByName(spanName)
        val exceptionEvents = spans.flatMap { it.events }.filter { it.name == EXCEPTION_EVENT_NAME }
        if (exceptionEvents.isNotEmpty()) {
            val recorded = exceptionEvents.map { it.attributes.toStringKeyMap() }
            fail("Expected no exception event on span '$spanName', but found: $recorded")
        }
    }

    private fun spansByName(name: String): List<SpanData> {
        val spans = finishedSpans()
        val matching = spans.filter { it.name == name }
        if (matching.isEmpty()) {
            val available = spans.map { it.name }.distinct()
            fail("Span '$name' was not recorded. Available spans: $available")
        }
        return matching
    }

    private fun List<SpanData>.formatEvents(): List<String> =
        flatMap { it.events }.map { "${it.name}(attrs=${it.attributes.toStringKeyMap()})" }

    private fun Attributes.containsAll(expected: Map<String, Any?>): Boolean {
        val actual = toStringKeyMap()
        return expected.all { (key, value) -> actual[key] == value }
    }

    private fun Attributes.toStringKeyMap(): Map<String, Any?> = asMap().mapKeys { entry -> entry.key.key }

    private companion object {
        const val EXCEPTION_EVENT_NAME = "exception"
        const val EXCEPTION_TYPE_KEY = "exception.type"
        const val EXCEPTION_MESSAGE_KEY = "exception.message"
    }
}
