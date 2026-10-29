package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.GatewayTimeoutException
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY

data class PaymentGatewayTimeoutException(
    val paymentMethod: PaymentMethod = TIMEOUT_GATEWAY,
    val latencyMs: Long = 1500L,
    override val cause: Throwable? = null,
    override val message: String = "Payment gateway timed out",
) : GatewayTimeoutException(message, cause) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentGatewayTimeoutException) return false
        return paymentMethod == other.paymentMethod && latencyMs == other.latencyMs
    }

    override fun hashCode(): Int = 31 * paymentMethod.hashCode() + latencyMs.hashCode()
}
