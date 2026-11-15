package com.observeit.observedapp.correlation.api

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.test.SpanCapture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.trace.SpanKind.SERVER
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class MdcCorrelationFilterTest {

    @JvmField
    @RegisterExtension
    val spanCapture = SpanCapture()

    private val filter = MdcCorrelationFilter()

    @Test
    fun `sets checkout scenario in MDC and active SERVER span during execution and cleans up in finally`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader("X-Checkout-Scenario", "payment-timeout")
        val response = MockHttpServletResponse()
        var observedMdcScenario: String? = null
        var observedContextScenario: String? = null
        val serverSpan = spanCapture.openTelemetry.getTracer("test")
            .spanBuilder("HTTP POST")
            .setSpanKind(SERVER)
            .startSpan()

        val filterChain = FilterChain { _, _ ->
            observedMdcScenario = MDC.get("checkout.scenario")
            observedContextScenario = CorrelationContext.getCheckoutScenario()
        }

        // when
        serverSpan.makeCurrent().use {
            filter.doFilter(request, response, filterChain)
        }
        serverSpan.end()

        // then
        observedMdcScenario shouldBe "payment-timeout"
        observedContextScenario shouldBe "payment-timeout"
        spanCapture.assertSpanRecorded(
            name = "HTTP POST",
            kind = SERVER,
            attributes = mapOf("checkout.scenario" to "payment-timeout"),
        )

        // and
        MDC.get("checkout.scenario").shouldBeNull()
        CorrelationContext.getCheckoutScenario().shouldBeNull()
    }

    @Test
    fun `guarantees MDC cleanup even when exception occurs during chain execution`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader("X-Checkout-Scenario", "exception-scenario")
        val response = MockHttpServletResponse()

        val filterChain = FilterChain { _, _ ->
            CorrelationContext.populateOrderCorrelation("ord-test", "cust-test")
            error("Simulated error")
        }

        // when
        val exception = shouldThrow<IllegalStateException> {
            filter.doFilter(request, response, filterChain)
        }

        // then
        exception.message shouldBe "Simulated error"

        // and
        MDC.get("checkout.scenario").shouldBeNull()
        MDC.get("order.id").shouldBeNull()
        MDC.get("customer.id").shouldBeNull()
    }
}
