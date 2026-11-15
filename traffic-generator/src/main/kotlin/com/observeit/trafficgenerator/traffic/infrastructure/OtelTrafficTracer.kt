package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.ScenarioExecutionResult
import com.observeit.trafficgenerator.traffic.domain.ScenarioSpan
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.ErrorType
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.Key
import com.observeit.trafficgenerator.traffic.domain.TrafficTracer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind.INTERNAL
import io.opentelemetry.api.trace.StatusCode.ERROR
import org.springframework.stereotype.Component

@Component
class OtelTrafficTracer(openTelemetry: OpenTelemetry) : TrafficTracer {

    private val tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)

    @Suppress("TooGenericExceptionCaught")
    override fun traceScenario(
        scenario: CheckoutScenario,
        customerId: String,
        block: (ScenarioSpan) -> ScenarioExecutionResult,
    ): ScenarioExecutionResult {
        val span = tracer.spanBuilder(SPAN_NAME)
            .setSpanKind(INTERNAL)
            .startSpan()
        span.setAttribute(Key.CHECKOUT_SCENARIO, scenario.name)
        span.setAttribute(Key.CUSTOMER_ID, customerId)

        val scenarioSpan = object : ScenarioSpan {
            override fun recordOrderCreated(orderId: String) {
                span.setAttribute(Key.ORDER_ID, orderId)
            }

            override fun recordTimeout() {
                span.setAttribute(Key.ERROR_TYPE, ErrorType.GATEWAY_TIMEOUT)
                span.setStatus(ERROR, GATEWAY_TIMEOUT_STATUS_DESCRIPTION)
            }
        }

        return try {
            span.makeCurrent().use {
                val result = block(scenarioSpan)
                span.setAttribute(Key.ORDER_STATE, result.finalState)
                result
            }
        } catch (exception: Throwable) {
            span.recordException(exception)
            span.setStatus(ERROR)
            throw exception
        } finally {
            span.end()
        }
    }

    private companion object {
        const val INSTRUMENTATION_SCOPE = "com.observeit.trafficgenerator.traffic"
        const val SPAN_NAME = "checkout.scenario"
        const val GATEWAY_TIMEOUT_STATUS_DESCRIPTION = "Payment gateway timed out"
    }
}
