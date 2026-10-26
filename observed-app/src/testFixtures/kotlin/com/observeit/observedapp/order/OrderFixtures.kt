package com.observeit.observedapp.order

import com.observeit.observedapp.order.api.CreateOrderLineItemRequest
import com.observeit.observedapp.order.api.CreateOrderRequest
import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderLineItem
import com.observeit.observedapp.order.domain.OrderState
import java.math.BigDecimal
import java.util.Currency

object OrderFixtures {

    fun anOrder(
        id: String = "id",
        customerId: String = "customerId",
        items: List<OrderLineItem> = listOf(anOrderLineItem()),
        state: OrderState = OrderState.CREATED,
    ) = Order(
        id = id,
        customerId = customerId,
        items = items,
        state = state,
    )

    fun anOrderLineItem(
        sku: String = "sku",
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("10.00"),
        currency: Currency = Currency.getInstance("USD"),
    ) = OrderLineItem(
        sku = sku,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
    )

    fun aCreateOrderRequest(
        customerId: String = "customerId",
        items: List<CreateOrderLineItemRequest> = listOf(aCreateOrderLineItemRequest()),
    ) = CreateOrderRequest(
        customerId = customerId,
        items = items,
    )

    fun aCreateOrderLineItemRequest(
        sku: String = "sku",
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("10.00"),
        currency: String = "USD",
    ) = CreateOrderLineItemRequest(
        sku = sku,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
    )
}
