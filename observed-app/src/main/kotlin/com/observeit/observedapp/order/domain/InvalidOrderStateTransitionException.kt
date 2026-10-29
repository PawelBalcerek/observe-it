package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.ConflictException

data class InvalidOrderStateTransitionException(
    val orderId: String,
    val state: OrderState,
    val attemptedAction: String,
) : ConflictException("Cannot $attemptedAction order $orderId in $state state")
