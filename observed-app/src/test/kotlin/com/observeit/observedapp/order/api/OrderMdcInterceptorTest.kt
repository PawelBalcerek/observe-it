package com.observeit.observedapp.order.api

import com.observeit.observedapp.correlation.CorrelationContext
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE

class OrderMdcInterceptorTest {

    private val interceptor = OrderMdcInterceptor()

    @AfterEach
    fun tearDown() {
        CorrelationContext.clear()
    }

    @Test
    fun `extracts orderId path variable and populates order id in MDC`() {
        // given
        val request = MockHttpServletRequest()
        request.setAttribute(
            URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            mapOf("orderId" to "ord-42"),
        )
        val response = MockHttpServletResponse()

        // when
        val result = interceptor.preHandle(request, response, Any())

        // then
        result shouldBe true

        // and
        MDC.get("order.id") shouldBe "ord-42"
    }

    @Test
    fun `does not modify MDC when path variable is missing`() {
        // given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        // when
        val result = interceptor.preHandle(request, response, Any())

        // then
        result shouldBe true

        // and
        MDC.get("order.id").shouldBeNull()
    }
}
