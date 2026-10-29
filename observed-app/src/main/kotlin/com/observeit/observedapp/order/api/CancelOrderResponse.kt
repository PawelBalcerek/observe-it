package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderState

data class CancelOrderResponse(val orderId: String, val state: OrderState) {
    companion object {
        fun from(order: Order): CancelOrderResponse = CancelOrderResponse(
            orderId = order.id,
            state = order.state,
        )
    }
}
