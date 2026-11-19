package com.observeit.observedapp.order

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderRepository
import java.util.concurrent.ConcurrentHashMap

class FakeOrderRepository : OrderRepository {

    private val storage = ConcurrentHashMap<String, Order>()

    override fun save(order: Order): Order {
        storage[order.id] = order
        return order
    }

    override fun findById(id: String): Order? = storage[id]

    override fun count(): Int = storage.size
}
