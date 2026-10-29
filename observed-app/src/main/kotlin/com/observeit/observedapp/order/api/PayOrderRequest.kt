package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.PaymentMethod
import jakarta.validation.constraints.NotNull

data class PayOrderRequest(
    @field:NotNull(message = "paymentMethod must not be null")
    val paymentMethod: PaymentMethod?,
) {
    fun toDomain(): PaymentMethod = checkNotNull(paymentMethod)
}
