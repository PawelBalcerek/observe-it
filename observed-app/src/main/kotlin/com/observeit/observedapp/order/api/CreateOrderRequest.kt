package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.OrderLineItem
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.Currency

data class CreateOrderRequest(
    @field:NotBlank(message = "customerId must not be blank")
    val customerId: String,
    @field:NotEmpty(message = "items must not be empty")
    @field:Valid
    val items: List<CreateOrderLineItemRequest>,
) {
    @AssertTrue(message = "All line items in an order must share the same currency")
    fun isSingleCurrency(): Boolean = items.map { it.currency }.distinct().size <= 1

    @AssertTrue(message = "All line items must have a valid ISO-4217 currency")
    fun isValidCurrency(): Boolean = runCatching {
        items.forEach { Currency.getInstance(it.currency) }
    }.isSuccess

    fun toDomainLineItems(): List<OrderLineItem> = items.map { it.toDomain() }
}

data class CreateOrderLineItemRequest(
    @field:NotBlank(message = "sku must not be blank")
    val sku: String,
    @field:Positive(message = "quantity must be greater than zero")
    val quantity: Int,
    @field:Positive(message = "unitPrice must be greater than zero")
    val unitPrice: BigDecimal,
    @field:NotBlank(message = "currency must not be blank")
    val currency: String,
) {
    fun toDomain(): OrderLineItem = OrderLineItem(
        sku = sku,
        quantity = quantity,
        unitPrice = unitPrice,
        currency = Currency.getInstance(currency),
    )
}
