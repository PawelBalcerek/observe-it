package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@RequestBody @Valid request: CreateOrderRequest): OrderResponse {
        val order = orderService.createOrder(
            customerId = request.customerId,
            items = request.toDomainLineItems(),
        )
        return OrderResponse.from(order)
    }
}
