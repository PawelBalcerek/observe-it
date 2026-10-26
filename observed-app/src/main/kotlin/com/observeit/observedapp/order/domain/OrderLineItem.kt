package com.observeit.observedapp.order.domain

import java.math.BigDecimal
import java.util.Currency

data class OrderLineItem(val sku: String, val quantity: Int, val unitPrice: BigDecimal, val currency: Currency)
