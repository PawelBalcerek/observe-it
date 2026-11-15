package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.test.SpanCapture
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import com.observeit.trafficgenerator.traffic.domain.ScenarioExecutionResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind.INTERNAL
import io.opentelemetry.api.trace.SpanKind.SERVER
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.api.trace.StatusCode.UNSET
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OtelTrafficTracerTest {

    @JvmField
    @RegisterExtension
    val spanCapture = SpanCapture()

    private val tracer = OtelTrafficTracer(spanCapture.openTelemetry)

    @Test
    fun `creates root checkout scenario span with attributes and unset status when no parent is active`() {
        // given
        var currentTraceIdDuringBlock: String? = null

        // when
        val result = tracer.traceScenario(HAPPY_PATH, "cust-123") { scenarioSpan ->
            currentTraceIdDuringBlock = Span.current().spanContext.traceId
            scenarioSpan.recordOrderCreated("order-123")
            ScenarioExecutionResult(
                scenario = HAPPY_PATH,
                orderId = "order-123",
                customerId = "cust-123",
                finalState = "SHIPPED",
                success = true,
            )
        }

        // then
        result.orderId shouldBe "order-123"
        Span.current().spanContext.isValid shouldBe false
        val span = spanCapture.assertSpanRecorded(
            name = "checkout.scenario",
            kind = INTERNAL,
            status = UNSET,
            parentSpanId = SpanId.getInvalid(),
            attributes = mapOf(
                "checkout.scenario" to "HAPPY_PATH",
                "customer.id" to "cust-123",
                "order.id" to "order-123",
                "order.state" to "SHIPPED",
            ),
        )
        span.traceId shouldBe currentTraceIdDuringBlock
        span.instrumentationScopeInfo.name shouldBe "com.observeit.trafficgenerator.traffic"
        spanCapture.assertNoExceptionRecorded("checkout.scenario")
    }

    @Test
    fun `parents checkout scenario span onto active SERVER span when triggered via HTTP`() {
        // given
        val serverSpan = spanCapture.openTelemetry.getTracer("test")
            .spanBuilder("HTTP POST /api/traffic/trigger/{scenario}")
            .setSpanKind(SERVER)
            .startSpan()

        // when
        serverSpan.makeCurrent().use {
            tracer.traceScenario(HAPPY_PATH, "cust-456") { scenarioSpan ->
                scenarioSpan.recordOrderCreated("order-456")
                ScenarioExecutionResult(
                    scenario = HAPPY_PATH,
                    orderId = "order-456",
                    customerId = "cust-456",
                    finalState = "SHIPPED",
                    success = true,
                )
            }
        }
        serverSpan.end()

        // then
        spanCapture.assertSpanRecorded(
            name = "checkout.scenario",
            kind = INTERNAL,
            status = UNSET,
            traceId = serverSpan.spanContext.traceId,
            parentSpanId = serverSpan.spanContext.spanId,
            attributes = mapOf(
                "checkout.scenario" to "HAPPY_PATH",
                "customer.id" to "cust-456",
                "order.id" to "order-456",
                "order.state" to "SHIPPED",
            ),
        )
    }

    @Test
    fun `records gateway timeout error attribute and ERROR status when recordTimeout is called`() {
        // given
        val customerId = "cust-timeout"
        val orderId = "order-timeout"

        // when
        tracer.traceScenario(PAYMENT_GATEWAY_TIMEOUT, customerId) { scenarioSpan ->
            scenarioSpan.recordOrderCreated(orderId)
            scenarioSpan.recordTimeout()
            ScenarioExecutionResult(
                scenario = PAYMENT_GATEWAY_TIMEOUT,
                orderId = orderId,
                customerId = customerId,
                finalState = "FAILED",
                success = true,
            )
        }

        // then
        val span = spanCapture.assertSpanRecorded(
            name = "checkout.scenario",
            kind = INTERNAL,
            status = ERROR,
            attributes = mapOf(
                "checkout.scenario" to "PAYMENT_GATEWAY_TIMEOUT",
                "customer.id" to "cust-timeout",
                "order.id" to "order-timeout",
                "order.state" to "FAILED",
                "error.type" to "GATEWAY_TIMEOUT",
            ),
        )
        span.status.description shouldBe "Payment gateway timed out"
        spanCapture.assertNoExceptionRecorded("checkout.scenario")
    }

    @Test
    fun `records exception and sets ERROR status when an unexpected exception escapes`() {
        // given
        val customerId = "cust-err"

        // when
        val exception = shouldThrow<IllegalStateException> {
            tracer.traceScenario(HAPPY_PATH, customerId) {
                error("Unexpected downstream failure")
            }
        }

        // then
        exception.message shouldBe "Unexpected downstream failure"
        Span.current().spanContext.isValid shouldBe false
        spanCapture.assertSpanRecorded(
            name = "checkout.scenario",
            kind = INTERNAL,
            status = ERROR,
            attributes = mapOf(
                "checkout.scenario" to "HAPPY_PATH",
                "customer.id" to "cust-err",
            ),
        )
        spanCapture.assertExceptionRecorded(
            spanName = "checkout.scenario",
            exceptionType = IllegalStateException::class,
            message = "Unexpected downstream failure",
        )
    }
}
