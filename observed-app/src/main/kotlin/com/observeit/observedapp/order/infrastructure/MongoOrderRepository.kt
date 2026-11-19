package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Repository

@Repository
class MongoOrderRepository(private val mongoTemplate: MongoTemplate) : OrderRepository {

    override fun save(order: Order): Order = mongoTemplate.save(OrderDocument.from(order)).toDomain()

    override fun findById(id: String): Order? = mongoTemplate.findById(id, OrderDocument::class.java)?.toDomain()

    override fun count(): Int = mongoTemplate.estimatedCount(OrderDocument::class.java).toInt()
}
