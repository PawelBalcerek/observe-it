package com.observeit.observedapp.order.api

import com.observeit.observedapp.correlation.CorrelationContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class OrderMdcInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        @Suppress("UNCHECKED_CAST")
        val pathVariables = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        ) as? Map<String, String>
        val orderId = pathVariables?.get(ORDER_ID_PATH_VARIABLE)
        if (!orderId.isNullOrBlank()) {
            CorrelationContext.setOrderId(orderId)
        }
        return true
    }

    companion object {
        const val ORDER_ID_PATH_VARIABLE = "orderId"
    }
}
