package com.observeit.observedapp.order.api

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class OrderWebMvcConfigurer(private val orderMdcInterceptor: OrderMdcInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(orderMdcInterceptor)
    }
}
