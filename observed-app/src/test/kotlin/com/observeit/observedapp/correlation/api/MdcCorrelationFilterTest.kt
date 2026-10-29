package com.observeit.observedapp.correlation.api

import com.observeit.observedapp.correlation.CorrelationContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class MdcCorrelationFilterTest {

    private val filter = MdcCorrelationFilter()

    @Test
    fun `sets checkout scenario in MDC during execution and cleans up in finally`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader("X-Checkout-Scenario", "payment-timeout")
        val response = MockHttpServletResponse()
        var observedMdcScenario: String? = null

        val filterChain = FilterChain { _, _ ->
            observedMdcScenario = MDC.get("checkout.scenario")
        }

        // when
        filter.doFilter(request, response, filterChain)

        // then
        observedMdcScenario shouldBe "payment-timeout"

        // and
        MDC.get("checkout.scenario").shouldBeNull()
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
