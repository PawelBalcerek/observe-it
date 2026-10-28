package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderLineItem
import com.observeit.observedapp.order.domain.OrderState
import java.math.BigDecimal

data class OrderResponse(
    val orderId: String,
    val customerId: String,
    val state: OrderState,
    val items: List<OrderLineItemResponse>,
    val totalAmount: BigDecimal,
    val currency: String,
) {
    companion object {
        fun from(order: Order): OrderResponse = OrderResponse(
            orderId = order.id,
            customerId = order.customerId,
            state = order.state,
            items = order.items.map { OrderLineItemResponse.from(it) },
            totalAmount = order.totalAmount,
            currency = order.currency.currencyCode,
        )
    }
}

data class OrderLineItemResponse(val sku: String, val quantity: Int, val unitPrice: BigDecimal, val currency: String) {
    companion object {
        fun from(item: OrderLineItem): OrderLineItemResponse = OrderLineItemResponse(
            sku = item.sku,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            currency = item.currency.currencyCode,
        )
    }
}
