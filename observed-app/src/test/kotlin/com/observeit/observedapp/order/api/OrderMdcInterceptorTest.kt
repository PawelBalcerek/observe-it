package com.observeit.observedapp.order.api

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.correlation.CorrelationContext.ORDER_ID
import com.observeit.observedapp.order.api.OrderMdcInterceptor.Companion.ORDER_ID_PATH_VARIABLE
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
            mapOf(ORDER_ID_PATH_VARIABLE to "ord-42"),
        )
        val response = MockHttpServletResponse()

        // when
        val result = interceptor.preHandle(request, response, Any())

        // then
        result shouldBe true

        // and
        MDC.get(ORDER_ID) shouldBe "ord-42"
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
        MDC.get(ORDER_ID).shouldBeNull()
    }
}
