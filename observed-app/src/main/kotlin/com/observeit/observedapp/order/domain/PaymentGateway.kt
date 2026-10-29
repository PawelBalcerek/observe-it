package com.observeit.observedapp.order.domain

import java.math.BigDecimal
import java.util.Currency

interface PaymentGateway {
    fun processPayment(orderId: String, amount: BigDecimal, currency: Currency, paymentMethod: PaymentMethod)
}
