package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.UnprocessableEntityException

data class PaymentDeclinedException(val paymentMethod: PaymentMethod, val latencyMs: Long = 50L) :
    UnprocessableEntityException("Payment declined for method ${paymentMethod.name}")
