package com.observeit.observedapp.order.domain

import com.observeit.observedapp.correlation.CorrelationContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderService(private val orderRepository: OrderRepository) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    fun createOrder(customerId: String, items: List<OrderLineItem>): Order {
        val orderId = UUID.randomUUID().toString()
        CorrelationContext.populateOrderCorrelation(orderId, customerId)

        val order = Order(
            id = orderId,
            customerId = customerId,
            items = items,
            state = OrderState.CREATED,
        )

        val saved = orderRepository.save(order)

        logger.atInfo()
            .addKeyValue("order.state", saved.state.name)
            .addKeyValue("order.total_amount", saved.totalAmount.toDouble())
            .addKeyValue("order.currency", saved.currency.currencyCode)
            .addKeyValue("order.item_count", saved.itemCount)
            .log("Order created")

        return saved
    }

    fun getOrder(orderId: String): Order {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

        CorrelationContext.populateOrderCorrelation(order.id, order.customerId)

        logger.atInfo()
            .addKeyValue("order.state", order.state.name)
            .addKeyValue("order.total_amount", order.totalAmount.toDouble())
            .addKeyValue("order.currency", order.currency.currencyCode)
            .addKeyValue("order.item_count", order.itemCount)
            .log("Order retrieved")

        return order
    }
}
