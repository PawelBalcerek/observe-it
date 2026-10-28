package com.observeit.observedapp.order.domain

import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import java.math.BigDecimal
import java.util.Currency

data class Order(
    val id: String,
    val customerId: String,
    val items: List<OrderLineItem>,
    val state: OrderState = CREATED,
) {
    val currency: Currency
        get() = items.first().currency

    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + (item.unitPrice * item.quantity.toBigDecimal())
        }

    val itemCount: Long
        get() = items.sumOf { it.quantity.toLong() }

    fun reserve(): Order = copy(state = RESERVED)
}
