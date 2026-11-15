package com.observeit.observedapp.order.domain

interface OrderReservationSpan {
    fun recordInventoryReservation(
        sku: String,
        requestedQuantity: Long,
        availableQuantity: Long,
        outcome: InventoryOutcome,
    )

    fun recordStateTransition(previousState: OrderState, state: OrderState)
}

interface OrderTracer {
    fun traceCreateOrder(block: () -> Order): Order

    fun traceGetOrder(orderId: String, block: () -> Order): Order

    fun traceReserveOrder(orderId: String, block: (OrderReservationSpan) -> Order): Order

    fun tracePayOrder(orderId: String, paymentMethod: PaymentMethod, latencyMs: Long, block: () -> Order): Order

    fun tracePaymentExecution(order: Order, paymentMethod: PaymentMethod, latencyMs: Long, block: () -> Unit)

    fun traceShipOrder(orderId: String, shippingMethod: ShippingMethod, block: () -> Order): Order

    fun traceCancelOrder(
        orderId: String,
        reason: String,
        block: (recordPreviousState: (OrderState) -> Unit) -> Order,
    ): Order
}
