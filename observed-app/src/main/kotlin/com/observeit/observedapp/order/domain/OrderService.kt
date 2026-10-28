package com.observeit.observedapp.order.domain

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.domain.OrderState.CREATED
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
            state = CREATED,
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
        val order = loadOrderWithCorrelation(orderId)

        logger.atInfo()
            .addKeyValue("order.state", order.state.name)
            .addKeyValue("order.total_amount", order.totalAmount.toDouble())
            .addKeyValue("order.currency", order.currency.currencyCode)
            .addKeyValue("order.item_count", order.itemCount)
            .log("Order retrieved")

        return order
    }

    fun reserveOrder(orderId: String): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state
        val reserved = orderRepository.save(order.reserve())

        reserved.items.forEach { item ->
            logger.atDebug()
                .addKeyValue("inventory.sku", item.sku)
                .addKeyValue("inventory.requested_quantity", item.quantity.toLong())
                .addKeyValue("inventory.available_quantity", STANDARD_AVAILABLE_QUANTITY)
                .log("Inventory reserved for ${item.sku} (${item.quantity}/$STANDARD_AVAILABLE_QUANTITY)")
        }

        logger.atInfo()
            .addKeyValue("order.state", reserved.state.name)
            .addKeyValue("order.previous_state", previousState.name)
            .log("Order reserved")

        return reserved
    }

    private fun loadOrderWithCorrelation(orderId: String): Order {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)
        CorrelationContext.populateOrderCorrelation(order.id, order.customerId)
        return order
    }

    private companion object {
        const val STANDARD_AVAILABLE_QUANTITY = 100L
    }
}
