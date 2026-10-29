package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.NotFoundException

data class OrderNotFoundException(val orderId: String) : NotFoundException("Order not found: $orderId")
