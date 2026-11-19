package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.domain.Order
import com.observeit.observedapp.order.domain.OrderLineItem
import com.observeit.observedapp.order.domain.OrderState
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType.DECIMAL128
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

@Document(collection = "orders")
data class OrderDocument(
    @Id val id: String,
    val customerId: String,
    val items: List<OrderLineItemDocument>,
    val state: OrderState,
    @Indexed(
        name = "orders_updated_at_ttl_idx",
        expireAfter = "\${order.repository.ttl:1h}",
    )
    val updatedAt: Instant = Instant.now(),
) {
    fun toDomain(): Order = Order(
        id = id,
        customerId = customerId,
        items = items.map { it.toDomain() },
        state = state,
    )

    companion object {
        fun from(order: Order): OrderDocument = OrderDocument(
            id = order.id,
            customerId = order.customerId,
            items = order.items.map { OrderLineItemDocument.from(it) },
            state = order.state,
        )
    }
}

data class OrderLineItemDocument(
    val sku: String,
    val quantity: Int,
    @Field(targetType = DECIMAL128)
    val unitPrice: BigDecimal,
    val currency: Currency,
) {
    fun toDomain(): OrderLineItem = OrderLineItem(
        sku = sku,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
    )

    companion object {
        fun from(item: OrderLineItem): OrderLineItemDocument = OrderLineItemDocument(
            sku = item.sku,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            currency = item.currency,
        )
    }
}
