package com.observeit.observedapp.order.domain

enum class OrderState {
    CREATED,
    RESERVED,
    PAID,
    SHIPPED,
    FAILED,
    CANCELLED,
}
