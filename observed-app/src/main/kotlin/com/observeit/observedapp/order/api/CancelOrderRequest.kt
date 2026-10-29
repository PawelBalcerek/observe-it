package com.observeit.observedapp.order.api

import jakarta.validation.constraints.NotBlank

data class CancelOrderRequest(
    @field:NotBlank(message = "reason must not be blank")
    val reason: String?,
) {
    fun toDomain(): String = checkNotNull(reason)
}
