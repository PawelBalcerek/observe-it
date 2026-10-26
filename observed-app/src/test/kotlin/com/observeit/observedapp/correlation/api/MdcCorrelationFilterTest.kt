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
        request.addHeader(MdcCorrelationFilter.HEADER_CHECKOUT_SCENARIO, "payment-timeout")
        val response = MockHttpServletResponse()
        var observedMdcScenario: String? = null

        val filterChain = FilterChain { _, _ ->
            observedMdcScenario = MDC.get(CorrelationContext.CHECKOUT_SCENARIO)
        }

        // when
        filter.doFilter(request, response, filterChain)

        // then
        observedMdcScenario shouldBe "payment-timeout"

        // and
        MDC.get(CorrelationContext.CHECKOUT_SCENARIO).shouldBeNull()
    }

    @Test
    fun `guarantees MDC cleanup even when exception occurs during chain execution`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader(MdcCorrelationFilter.HEADER_CHECKOUT_SCENARIO, "exception-scenario")
        val response = MockHttpServletResponse()

        val filterChain = FilterChain { _, _ ->
            CorrelationContext.populateOrderCorrelation("ord-test", "cust-test")
            throw IllegalStateException("Simulated error")
        }

        // when
        val exception = shouldThrow<IllegalStateException> {
            filter.doFilter(request, response, filterChain)
        }

        // then
        exception.message shouldBe "Simulated error"

        // and
        MDC.get(CorrelationContext.CHECKOUT_SCENARIO).shouldBeNull()
        MDC.get(CorrelationContext.ORDER_ID).shouldBeNull()
        MDC.get(CorrelationContext.CUSTOMER_ID).shouldBeNull()
    }
}
