package com.observeit.observedapp.order.domain

import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderTelemetry.Key
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
            .addKeyValue(Key.ORDER_STATE, saved.state.name)
            .addKeyValue(Key.ORDER_TOTAL_AMOUNT, saved.totalAmount.toDouble())
            .addKeyValue(Key.ORDER_CURRENCY, saved.currency.currencyCode)
            .addKeyValue(Key.ORDER_ITEM_COUNT, saved.itemCount)
            .log("Order created")

        return saved
    }

    fun getOrder(orderId: String): Order {
        val order = loadOrderWithCorrelation(orderId)

        logger.atInfo()
            .addKeyValue(Key.ORDER_STATE, order.state.name)
            .addKeyValue(Key.ORDER_TOTAL_AMOUNT, order.totalAmount.toDouble())
            .addKeyValue(Key.ORDER_CURRENCY, order.currency.currencyCode)
            .addKeyValue(Key.ORDER_ITEM_COUNT, order.itemCount)
            .log("Order retrieved")

        return order
    }

    fun reserveOrder(orderId: String): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state
        val reserved = order.reserve()

        val outOfStockItem = order.items.firstOrNull { it.sku == SKU_OUT_OF_STOCK }
        if (outOfStockItem != null) {
            orderRepository.save(order.fail())
            throw OutOfStockException(
                sku = outOfStockItem.sku,
                requestedQuantity = outOfStockItem.quantity.toLong(),
                availableQuantity = OUT_OF_STOCK_AVAILABLE_QUANTITY,
            )
        }

        orderRepository.save(reserved)

        reserved.items.forEach { item ->
            if (item.sku == SKU_LOW_STOCK) {
                logger.atWarn()
                    .addKeyValue(Key.INVENTORY_SKU, item.sku)
                    .addKeyValue(Key.INVENTORY_REQUESTED_QUANTITY, item.quantity.toLong())
                    .addKeyValue(Key.INVENTORY_AVAILABLE_QUANTITY, LOW_STOCK_AVAILABLE_QUANTITY)
                    .log("Low stock for ${item.sku} (${item.quantity}/$LOW_STOCK_AVAILABLE_QUANTITY)")
            } else {
                logger.atDebug()
                    .addKeyValue(Key.INVENTORY_SKU, item.sku)
                    .addKeyValue(Key.INVENTORY_REQUESTED_QUANTITY, item.quantity.toLong())
                    .addKeyValue(Key.INVENTORY_AVAILABLE_QUANTITY, STANDARD_AVAILABLE_QUANTITY)
                    .log("Inventory reserved for ${item.sku} (${item.quantity}/$STANDARD_AVAILABLE_QUANTITY)")
            }
        }

        logger.atInfo()
            .addKeyValue(Key.ORDER_STATE, reserved.state.name)
            .addKeyValue(Key.ORDER_PREVIOUS_STATE, previousState.name)
            .log("Order reserved")

        return reserved
    }

    fun payOrder(orderId: String, paymentMethod: PaymentMethod): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state
        val paid = order.pay()

        runCatching { paymentGateway.processPayment(order.id, order.totalAmount, order.currency, paymentMethod) }
            .onFailure { orderRepository.save(order.fail()) }
            .getOrThrow()

        orderRepository.save(paid)

        logger.atInfo()
            .addKeyValue(Key.ORDER_STATE, paid.state.name)
            .addKeyValue(Key.ORDER_PREVIOUS_STATE, previousState.name)
            .addKeyValue(Key.PAYMENT_METHOD, paymentMethod.name)
            .addKeyValue(Key.PAYMENT_LATENCY_MS, STANDARD_PAYMENT_LATENCY_MS)
            .log("Order paid")

        return paid
    }

    fun shipOrder(orderId: String, shippingMethod: ShippingMethod): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state

        val shipped = orderRepository.save(order.ship())

        logger.atInfo()
            .addKeyValue(Key.ORDER_STATE, shipped.state.name)
            .addKeyValue(Key.ORDER_PREVIOUS_STATE, previousState.name)
            .addKeyValue(Key.SHIPPING_METHOD, shippingMethod.name)
            .log("Order shipped")

        return shipped
    }

    fun cancelOrder(orderId: String, reason: String): Order {
        val order = loadOrderWithCorrelation(orderId)
        val previousState = order.state

        val cancelled = orderRepository.save(order.cancel())

        logger.atInfo()
            .addKeyValue(Key.ORDER_STATE, cancelled.state.name)
            .addKeyValue(Key.ORDER_PREVIOUS_STATE, previousState.name)
            .addKeyValue(Key.CANCELLATION_REASON, reason)
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
