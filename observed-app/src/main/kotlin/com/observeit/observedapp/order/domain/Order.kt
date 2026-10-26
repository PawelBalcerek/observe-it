package com.observeit.observedapp.order.domain

import java.math.BigDecimal
import java.util.Currency

data class Order(
    val id: String,
    val customerId: String,
    val items: List<OrderLineItem>,
    val state: OrderState = OrderState.CREATED,
) {
    val currency: Currency
        get() = items.first().currency

    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + (item.unitPrice * item.quantity.toBigDecimal())
        }

    val itemCount: Long
        get() = items.sumOf { it.quantity.toLong() }
}
