package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Repository
class InMemoryOrderRepository(
    @Value("\${order.repository.capacity:10000}")
    private val capacity: Int = DEFAULT_CAPACITY,
) : OrderRepository {

    private val storage = ConcurrentHashMap<String, Order>()
    private val orderQueue = ConcurrentLinkedQueue<String>()

    override fun save(order: Order): Order {
        val previous = storage.put(order.id, order)
        if (previous == null) {
            orderQueue.add(order.id)
            evictIfNecessary()
        }
        return order
    }

    override fun findById(id: String): Order? = storage[id]

    override fun count(): Int = storage.size

    private fun evictIfNecessary() {
        while (storage.size > capacity) {
            val oldestId = orderQueue.poll() ?: break
            storage.remove(oldestId)
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 10_000
    }
}
