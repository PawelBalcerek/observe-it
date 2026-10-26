package com.observeit.observedapp.order.domain

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: String): Order?
    fun count(): Int
}
