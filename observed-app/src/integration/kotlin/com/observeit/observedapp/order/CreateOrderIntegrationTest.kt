package com.observeit.observedapp.order

import ch.qos.logback.classic.Level
import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.correlation.CorrelationContext
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderLineItemRequest
import com.observeit.observedapp.order.OrderFixtures.aCreateOrderRequest
import com.observeit.observedapp.order.api.CreateOrderRequest
import com.observeit.observedapp.order.api.OrderApiFixture
import com.observeit.observedapp.order.domain.OrderState
import com.observeit.observedapp.test.LogCapture
import com.observeit.observedapp.test.shouldBeValidationError
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.FieldSource
import java.math.BigDecimal

class CreateOrderIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val orderApi by lazy { OrderApiFixture(restTestClient) }

    @Test
    fun `post orders creates order with 201 Created and emits structured log with key-value pairs and MDC`() {
        // given
        val request = aCreateOrderRequest(
            customerId = "cust-42",
            items = listOf(
                aCreateOrderLineItemRequest(quantity = 2, unitPrice = BigDecimal("15.50"), currency = "EUR"),
                aCreateOrderLineItemRequest(quantity = 3, unitPrice = BigDecimal("2.00"), currency = "EUR"),
            ),
        )

        // when
        val response = orderApi.createOrderAndExpect201(request, checkoutScenario = "happy-path")

        // then
        assertSoftly(response) {
            orderId.shouldNotBeBlank()
            state shouldBe OrderState.CREATED
        }

        // and
        logCapture.assertLogged(
            level = Level.INFO,
            message = "Order created",
            keyValues = mapOf(
                "order.state" to "CREATED",
                "order.total_amount" to 37.0,
                "order.currency" to "EUR",
                "order.item_count" to 5L,
            ),
            mdc = mapOf(
                CorrelationContext.CHECKOUT_SCENARIO to "happy-path",
                CorrelationContext.ORDER_ID to response.orderId,
                CorrelationContext.CUSTOMER_ID to "cust-42",
            ),
        )
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("invalidOrderRequests")
    fun `post orders returns 400 Bad Request when request validation fails`(
        description: String,
        request: CreateOrderRequest,
        expectedError: String,
    ) {
        // when
        val problem = orderApi.createOrderAndExpect400(request)

        // then
        problem.shouldBeValidationError(expectedError)
    }

    companion object {
        @JvmStatic
        val invalidOrderRequests = listOf(
            Arguments.of(
                "customerId is blank",
                aCreateOrderRequest(customerId = ""),
                "customerId must not be blank",
            ),
            Arguments.of(
                "items list is empty",
                aCreateOrderRequest(items = emptyList()),
                "items must not be empty",
            ),
            Arguments.of(
                "line items contain mixed currencies",
                aCreateOrderRequest(
                    items = listOf(
                        aCreateOrderLineItemRequest(currency = "EUR"),
                        aCreateOrderLineItemRequest(currency = "USD"),
                    ),
                ),
                "All line items in an order must share the same currency",
            ),
            Arguments.of(
                "line item has invalid currency code",
                aCreateOrderRequest(
                    items = listOf(aCreateOrderLineItemRequest(currency = "INVALID")),
                ),
                "All line items must have a valid ISO-4217 currency",
            ),
            Arguments.of(
                "line item sku is blank",
                aCreateOrderRequest(
                    items = listOf(aCreateOrderLineItemRequest(sku = "")),
                ),
                "sku must not be blank",
            ),
            Arguments.of(
                "line item quantity is not positive",
                aCreateOrderRequest(
                    items = listOf(aCreateOrderLineItemRequest(quantity = 0)),
                ),
                "quantity must be greater than zero",
            ),
            Arguments.of(
                "line item unitPrice is not positive",
                aCreateOrderRequest(
                    items = listOf(aCreateOrderLineItemRequest(unitPrice = BigDecimal.ZERO)),
                ),
                "unitPrice must be greater than zero",
            ),
            Arguments.of(
                "line item currency is blank",
                aCreateOrderRequest(
                    items = listOf(aCreateOrderLineItemRequest(currency = "")),
                ),
                "currency must not be blank",
            ),
        )
    }
}
