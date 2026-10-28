package com.observeit.observedapp.order.api

import jakarta.validation.constraints.NotBlank

data class PayOrderRequest(
    @field:NotBlank(message = "paymentMethod must not be blank")
    val paymentMethod: String,
)
