package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.GatewayTimeoutException

data class PaymentGatewayTimeoutException(
    val paymentMethod: PaymentMethod,
    val latencyMs: Long,
    override val cause: Throwable,
) : GatewayTimeoutException("Payment gateway timed out", cause) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentGatewayTimeoutException) return false
        return paymentMethod == other.paymentMethod && latencyMs == other.latencyMs
    }

    override fun hashCode(): Int = 31 * paymentMethod.hashCode() + latencyMs.hashCode()
}
