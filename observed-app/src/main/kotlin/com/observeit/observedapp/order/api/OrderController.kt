package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.CREATED
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping
    @ResponseStatus(CREATED)
    fun createOrder(@RequestBody @Valid request: CreateOrderRequest): CreateOrderResponse {
        val order = orderService.createOrder(
            customerId = request.customerId,
            items = request.toDomainLineItems(),
        )
        return CreateOrderResponse.from(order)
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): OrderResponse {
        val order = orderService.getOrder(orderId)
        return OrderResponse.from(order)
    }

    @PostMapping("/{orderId}/reserve")
    fun reserveOrder(@PathVariable orderId: String): ReserveOrderResponse {
        val order = orderService.reserveOrder(orderId)
        return ReserveOrderResponse.from(order)
    }
}
