package com.observeit.observedapp.order.domain

import java.util.Currency

interface OrderMetrics {
    fun recordOrderCreated(currency: Currency)

    fun recordStateTransition(previousState: OrderState, newState: OrderState)

    fun recordError(errorType: String)

    fun recordInventoryReservation(sku: String, outcome: InventoryOutcome)

    fun recordPaymentDuration(method: PaymentMethod, outcome: PaymentOutcome, durationMs: Double)
}
