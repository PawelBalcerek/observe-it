package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.UnprocessableEntityException

data class OutOfStockException(val sku: String, val requestedQuantity: Long = 1L, val availableQuantity: Long = 0L) :
    UnprocessableEntityException("Inventory out of stock for $sku")
