package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderState

data class PayOrderResponse(val orderId: String, val state: OrderState) {
    companion object {
        fun from(order: Order): PayOrderResponse = PayOrderResponse(
            orderId = order.id,
            state = order.state,
        )
    }
}
