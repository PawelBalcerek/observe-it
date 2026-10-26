package com.observeit.observedapp.correlation.api

import com.observeit.observedapp.correlation.CorrelationContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcCorrelationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val scenario = request.getHeader(HEADER_CHECKOUT_SCENARIO)
        if (!scenario.isNullOrBlank()) {
            CorrelationContext.setCheckoutScenario(scenario)
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            CorrelationContext.clear()
        }
    }

    companion object {
        const val HEADER_CHECKOUT_SCENARIO = "X-Checkout-Scenario"
    }
}
