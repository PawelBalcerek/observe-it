package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.ShippingMethod
import jakarta.validation.constraints.NotNull

data class ShipOrderRequest(
    @field:NotNull(message = "shippingMethod must not be null")
    val shippingMethod: ShippingMethod?,
) {
    fun toDomain(): ShippingMethod = checkNotNull(shippingMethod)
}
