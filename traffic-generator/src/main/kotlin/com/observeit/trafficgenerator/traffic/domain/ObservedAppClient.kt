package com.observeit.trafficgenerator.traffic.domain

import java.math.BigDecimal

data class CreateOrderItem(val sku: String, val quantity: Int, val unitPrice: BigDecimal, val currency: String)

data class CreateOrderResult(val orderId: String, val customerId: String, val state: String)

sealed interface ReserveOrderResult {
    data class Success(val orderId: String, val state: String) : ReserveOrderResult
    data class OutOfStock(val detail: String) : ReserveOrderResult
}

sealed interface PayOrderResult {
    data class Success(val orderId: String, val state: String) : PayOrderResult
    data class Declined(val detail: String) : PayOrderResult
    data class Timeout(val detail: String) : PayOrderResult
    data class InvalidTransition(val detail: String) : PayOrderResult
}

sealed interface ShipOrderResult {
    data class Success(val orderId: String, val state: String, val shippingMethod: String) : ShipOrderResult
}

sealed interface CancelOrderResult {
    data class Success(val orderId: String, val state: String, val reason: String) : CancelOrderResult
}

data class OrderDetails(
    val orderId: String,
    val customerId: String,
    val state: String,
    val totalAmount: Double,
    val currency: String,
    val itemCount: Long,
)

interface ObservedAppClient {
    fun createOrder(scenario: CheckoutScenario, customerId: String, items: List<CreateOrderItem>): CreateOrderResult

    fun reserveOrder(scenario: CheckoutScenario, orderId: String): ReserveOrderResult

    fun payOrder(scenario: CheckoutScenario, orderId: String, paymentMethod: String): PayOrderResult

    fun shipOrder(scenario: CheckoutScenario, orderId: String, shippingMethod: String): ShipOrderResult

    fun cancelOrder(scenario: CheckoutScenario, orderId: String, reason: String): CancelOrderResult

    fun getOrder(scenario: CheckoutScenario, orderId: String): OrderDetails
}
