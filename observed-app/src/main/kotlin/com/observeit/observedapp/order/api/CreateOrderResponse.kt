package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderState

data class CreateOrderResponse(val orderId: String, val state: OrderState) {
    companion object {
        fun from(order: Order): CreateOrderResponse = CreateOrderResponse(
            orderId = order.id,
            state = order.state,
        )
    }
}
