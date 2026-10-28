package com.observeit.observedapp.correlation.api

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.correlation.CorrelationContext.CHECKOUT_SCENARIO
import com.observeit.observedapp.correlation.CorrelationContext.CUSTOMER_ID
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.correlation.api.MdcCorrelationFilter.Companion.HEADER_CHECKOUT_SCENARIO
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
        request.addHeader(HEADER_CHECKOUT_SCENARIO, "payment-timeout")
        val response = MockHttpServletResponse()
        var observedMdcScenario: String? = null

        val filterChain = FilterChain { _, _ ->
            observedMdcScenario = MDC.get(CHECKOUT_SCENARIO)
        }

        // when
        filter.doFilter(request, response, filterChain)

        // then
        observedMdcScenario shouldBe "payment-timeout"

        // and
        MDC.get(CHECKOUT_SCENARIO).shouldBeNull()
    }

    @Test
    fun `guarantees MDC cleanup even when exception occurs during chain execution`() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader(HEADER_CHECKOUT_SCENARIO, "exception-scenario")
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
        MDC.get(CHECKOUT_SCENARIO).shouldBeNull()
        MDC.get(ORDER_ID).shouldBeNull()
        MDC.get(CUSTOMER_ID).shouldBeNull()
    }
}
