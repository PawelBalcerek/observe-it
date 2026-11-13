package com.observeit.observedapp.order.domain

import com.observeit.observedapp.domain.UnprocessableEntityException

data class OutOfStockException(val sku: String, val requestedQuantity: Long, val availableQuantity: Long) :
    UnprocessableEntityException("Inventory out of stock for $sku")
