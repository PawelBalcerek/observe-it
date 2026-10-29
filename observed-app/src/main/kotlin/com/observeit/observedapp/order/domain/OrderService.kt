package com.observeit.observedapp.order.domain

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.domain.OrderState.CREATED
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderService(private val orderRepository: OrderRepository, private val paymentGateway: PaymentGateway) {
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

        if (!order.canReserve()) {
            throw InvalidOrderStateTransitionException(orderId, order.state, "reserve")
        }

        val outOfStockItem = order.items.firstOrNull { it.sku == SKU_OUT_OF_STOCK }
        if (outOfStockItem != null) {
            orderRepository.save(order.fail())
            throw OutOfStockException(
                sku = outOfStockItem.sku,
                requestedQuantity = outOfStockItem.quantity.toLong(),
                availableQuantity = OUT_OF_STOCK_AVAILABLE_QUANTITY,
            )
        }

        val reserved = orderRepository.save(order.reserve())

        reserved.items.forEach { item ->
            if (item.sku == SKU_LOW_STOCK) {
                logger.atWarn()
                    .addKeyValue("inventory.sku", item.sku)
                    .addKeyValue("inventory.requested_quantity", item.quantity.toLong())
                    .addKeyValue("inventory.available_quantity", LOW_STOCK_AVAILABLE_QUANTITY)
                    .log("Low stock for ${item.sku} (${item.quantity}/$LOW_STOCK_AVAILABLE_QUANTITY)")
            } else {
                logger.atDebug()
                    .addKeyValue("inventory.sku", item.sku)
                    .addKeyValue("inventory.requested_quantity", item.quantity.toLong())
                    .addKeyValue("inventory.available_quantity", STANDARD_AVAILABLE_QUANTITY)
                    .log("Inventory reserved for ${item.sku} (${item.quantity}/$STANDARD_AVAILABLE_QUANTITY)")
            }
        }

        logger.atInfo()
            .addKeyValue("order.state", reserved.state.name)
            .addKeyValue("order.previous_state", previousState.name)
            .log("Order reserved")

        return reserved
    }

    fun payOrder(orderId: String, paymentMethod: PaymentMethod): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state

        if (!order.canPay()) {
            throw InvalidOrderStateTransitionException(orderId, order.state, "pay")
        }

        try {
            paymentGateway.processPayment(order.id, order.totalAmount, order.currency, paymentMethod)
        } catch (e: Exception) {
            orderRepository.save(order.fail())
            throw e
        }

        val paid = orderRepository.save(order.pay())

        logger.atInfo()
            .addKeyValue("order.state", paid.state.name)
            .addKeyValue("order.previous_state", previousState.name)
            .addKeyValue("payment.method", paymentMethod.name)
            .addKeyValue("payment.latency_ms", STANDARD_PAYMENT_LATENCY_MS)
            .log("Order paid")

        return paid
    }

    fun shipOrder(orderId: String, shippingMethod: ShippingMethod): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state

        val shipped = orderRepository.save(order.ship())

        logger.atInfo()
            .addKeyValue("order.state", shipped.state.name)
            .addKeyValue("order.previous_state", previousState.name)
            .addKeyValue("shipping.method", shippingMethod.name)
            .log("Order shipped")

        return shipped
    }

    fun cancelOrder(orderId: String, reason: String): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state

        val cancelled = orderRepository.save(order.cancel())

        logger.atInfo()
            .addKeyValue("order.state", cancelled.state.name)
            .addKeyValue("order.previous_state", previousState.name)
            .addKeyValue("cancellation.reason", reason)
            .log("Order cancelled")

        return cancelled
    }

    private fun loadOrderWithCorrelation(orderId: String): Order {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)
        CorrelationContext.populateOrderCorrelation(order.id, order.customerId)
        return order
    }

    private companion object {
        const val SKU_LOW_STOCK = "SKU-LOW-STOCK"
        const val SKU_OUT_OF_STOCK = "SKU-OUT-OF-STOCK"
        const val STANDARD_AVAILABLE_QUANTITY = 100L
        const val LOW_STOCK_AVAILABLE_QUANTITY = 2L
        const val OUT_OF_STOCK_AVAILABLE_QUANTITY = 0L
        const val STANDARD_PAYMENT_LATENCY_MS = 50L
    }
}
