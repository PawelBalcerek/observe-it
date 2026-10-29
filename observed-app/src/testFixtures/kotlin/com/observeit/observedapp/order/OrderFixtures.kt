package com.observeit.observedapp.order

import com.observeit.observedapp.order.api.CancelOrderRequest
import com.observeit.observedapp.order.api.CancelOrderResponse
import com.observeit.observedapp.order.api.CreateOrderLineItemRequest
import com.observeit.observedapp.order.api.CreateOrderRequest
import com.observeit.observedapp.order.api.CreateOrderResponse
import com.observeit.observedapp.order.api.OrderLineItemResponse
import com.observeit.observedapp.order.api.OrderResponse
import com.observeit.observedapp.order.api.PayOrderRequest
import com.observeit.observedapp.order.api.PayOrderResponse
import com.observeit.observedapp.order.api.ReserveOrderResponse
import com.observeit.observedapp.order.api.ShipOrderRequest
import com.observeit.observedapp.order.api.ShipOrderResponse
import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderLineItem
import com.observeit.observedapp.order.domain.OrderState
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.ShippingMethod
import com.observeit.observedapp.order.domain.ShippingMethod.STANDARD
import java.math.BigDecimal
import java.util.Currency

object OrderFixtures {

    fun anOrder(
        id: String = "id",
        customerId: String = "customerId",
        items: List<OrderLineItem> = listOf(anOrderLineItem()),
        state: OrderState = CREATED,
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

    fun aCreateOrderResponse(orderId: String = "id", state: OrderState = CREATED) = CreateOrderResponse(
        orderId = orderId,
        state = state,
    )

    fun aReserveOrderResponse(orderId: String = "id", state: OrderState = RESERVED) = ReserveOrderResponse(
        orderId = orderId,
        state = state,
    )

    fun aPayOrderRequest(paymentMethod: PaymentMethod? = CREDIT_CARD) = PayOrderRequest(
        paymentMethod = paymentMethod,
    )

    fun aPayOrderResponse(orderId: String = "id", state: OrderState = PAID) = PayOrderResponse(
        orderId = orderId,
        state = state,
    )

    fun aShipOrderRequest(shippingMethod: ShippingMethod? = STANDARD) = ShipOrderRequest(
        shippingMethod = shippingMethod,
    )

    fun aShipOrderResponse(orderId: String = "id", state: OrderState = SHIPPED) = ShipOrderResponse(
        orderId = orderId,
        state = state,
    )

    fun aCancelOrderRequest(reason: String? = "reason") = CancelOrderRequest(
        reason = reason,
    )

    fun aCancelOrderResponse(orderId: String = "id", state: OrderState = CANCELLED) = CancelOrderResponse(
        orderId = orderId,
        state = state,
    )

    fun anOrderResponse(
        orderId: String = "id",
        customerId: String = "customerId",
        state: OrderState = CREATED,
        items: List<OrderLineItemResponse> = listOf(anOrderLineItemResponse()),
        totalAmount: BigDecimal = BigDecimal("10.00"),
        currency: String = "USD",
    ) = OrderResponse(
        orderId = orderId,
        customerId = customerId,
        state = state,
        items = items,
        totalAmount = totalAmount,
        currency = currency,
    )

    fun anOrderLineItemResponse(
        sku: String = "sku",
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("10.00"),
        currency: String = "USD",
    ) = OrderLineItemResponse(
        sku = sku,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
    )
}
