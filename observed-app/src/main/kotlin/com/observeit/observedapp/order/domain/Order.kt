package com.observeit.observedapp.order.domain

import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
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

    private fun canReserve(): Boolean = state == CREATED

    private fun canPay(): Boolean = state == RESERVED

    private fun canShip(): Boolean = state == PAID

    private fun canCancel(): Boolean = state == CREATED || state == RESERVED

    fun reserve(): Order {
        if (!canReserve()) {
            throw InvalidOrderStateTransitionException(id, state, "reserve")
        }
        return copy(state = RESERVED)
    }

    fun pay(): Order {
        if (!canPay()) {
            throw InvalidOrderStateTransitionException(id, state, "pay")
        }
        return copy(state = PAID)
    }

    fun ship(): Order {
        if (!canShip()) {
            throw InvalidOrderStateTransitionException(id, state, "ship")
        }
        return copy(state = SHIPPED)
    }

    fun cancel(): Order {
        if (!canCancel()) {
            throw InvalidOrderStateTransitionException(id, state, "cancel")
        }
        return copy(state = CANCELLED)
    }

    fun fail(): Order = copy(state = FAILED)
}
