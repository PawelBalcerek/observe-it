package com.observeit.observedapp.order.api

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.test.SpanCapture
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.trace.SpanKind.SERVER
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE

class OrderMdcInterceptorTest {

    @JvmField
    @RegisterExtension
    val spanCapture = SpanCapture()

    private val interceptor = OrderMdcInterceptor()

    @AfterEach
    fun tearDown() {
        CorrelationContext.clear()
    }

    @Test
    fun `extracts orderId path variable and populates order id in MDC and active SERVER span`() {
        // given
        val request = MockHttpServletRequest()
        request.setAttribute(
            URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            mapOf("orderId" to "ord-42"),
        )
        val response = MockHttpServletResponse()
        val serverSpan = spanCapture.openTelemetry.getTracer("test")
            .spanBuilder("HTTP GET")
            .setSpanKind(SERVER)
            .startSpan()

        // when
        val result = serverSpan.makeCurrent().use {
            interceptor.preHandle(request, response, Any())
        }
        serverSpan.end()

        // then
        result shouldBe true

        // and
        MDC.get("order.id") shouldBe "ord-42"
        spanCapture.assertSpanRecorded(
            name = "HTTP GET",
            kind = SERVER,
            attributes = mapOf("order.id" to "ord-42"),
        )
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
