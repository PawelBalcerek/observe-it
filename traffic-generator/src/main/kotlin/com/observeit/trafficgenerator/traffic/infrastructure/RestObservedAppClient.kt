package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.domain.CancelOrderResult
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.CreateOrderItem
import com.observeit.trafficgenerator.traffic.domain.CreateOrderResult
import com.observeit.trafficgenerator.traffic.domain.ObservedAppClient
import com.observeit.trafficgenerator.traffic.domain.OrderDetails
import com.observeit.trafficgenerator.traffic.domain.PayOrderResult
import com.observeit.trafficgenerator.traffic.domain.ReserveOrderResult
import com.observeit.trafficgenerator.traffic.domain.ShipOrderResult
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.GATEWAY_TIMEOUT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal
import java.nio.charset.StandardCharsets

@Component
@Suppress("ElseCaseInsteadOfExhaustiveWhen")
class RestObservedAppClient(private val restClient: RestClient) : ObservedAppClient {

    override fun createOrder(
        scenario: CheckoutScenario,
        customerId: String,
        items: List<CreateOrderItem>,
    ): CreateOrderResult {
        val request = CreateOrderPayload(
            customerId = customerId,
            items = items.map {
                CreateOrderLineItemPayload(
                    sku = it.sku,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    currency = it.currency,
                )
            },
        )
        return restClient.post()
            .uri("/api/orders")
            .header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue)
            .contentType(APPLICATION_JSON)
            .body(request)
            .exchange { _, response ->
                when (response.statusCode) {
                    CREATED -> {
                        val body = response.bodyTo(CreateOrderResponsePayload::class.java)
                            ?: throw unexpectedStatus(response)
                        CreateOrderResult(
                            orderId = body.orderId,
                            customerId = customerId,
                            state = body.state,
                        )
                    }
                    else -> throw unexpectedStatus(response)
                }
            }
    }

    override fun reserveOrder(scenario: CheckoutScenario, orderId: String): ReserveOrderResult = restClient.post()
        .uri("/api/orders/{orderId}/reserve", orderId)
        .header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue)
        .exchange { _, response ->
            when (response.statusCode) {
                OK -> {
                    val body = response.bodyTo(ReserveOrderResponsePayload::class.java)
                        ?: throw unexpectedStatus(response)
                    ReserveOrderResult.Success(orderId = body.orderId, state = body.state)
                }
                UNPROCESSABLE_CONTENT -> {
                    val problem = response.bodyTo(ProblemDetail::class.java)
                    ReserveOrderResult.OutOfStock(detail = problem?.detail ?: "Out of stock")
                }
                else -> throw unexpectedStatus(response)
            }
        }

    override fun payOrder(scenario: CheckoutScenario, orderId: String, paymentMethod: String): PayOrderResult {
        val request = PayOrderPayload(paymentMethod = paymentMethod)
        return restClient.post()
            .uri("/api/orders/{orderId}/pay", orderId)
            .header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue)
            .contentType(APPLICATION_JSON)
            .body(request)
            .exchange { _, response ->
                when (response.statusCode) {
                    OK -> {
                        val body = response.bodyTo(PayOrderResponsePayload::class.java)
                            ?: throw unexpectedStatus(response)
                        PayOrderResult.Success(orderId = body.orderId, state = body.state)
                    }
                    UNPROCESSABLE_CONTENT -> {
                        val problem = response.bodyTo(ProblemDetail::class.java)
                        PayOrderResult.Declined(detail = problem?.detail ?: "Payment declined")
                    }
                    GATEWAY_TIMEOUT -> {
                        val problem = response.bodyTo(ProblemDetail::class.java)
                        PayOrderResult.Timeout(detail = problem?.detail ?: "Payment gateway timed out")
                    }
                    CONFLICT -> {
                        val problem = response.bodyTo(ProblemDetail::class.java)
                        PayOrderResult.InvalidTransition(detail = problem?.detail ?: "Invalid state transition")
                    }
                    else -> throw unexpectedStatus(response)
                }
            }
    }

    override fun shipOrder(scenario: CheckoutScenario, orderId: String, shippingMethod: String): ShipOrderResult {
        val request = ShipOrderPayload(shippingMethod = shippingMethod)
        return restClient.post()
            .uri("/api/orders/{orderId}/ship", orderId)
            .header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue)
            .contentType(APPLICATION_JSON)
            .body(request)
            .exchange { _, response ->
                when (response.statusCode) {
                    OK -> {
                        val body = response.bodyTo(ShipOrderResponsePayload::class.java)
                            ?: throw unexpectedStatus(response)
                        ShipOrderResult.Success(
                            orderId = body.orderId,
                            state = body.state,
                            shippingMethod = shippingMethod,
                        )
                    }
                    else -> throw unexpectedStatus(response)
                }
            }
    }

    override fun cancelOrder(scenario: CheckoutScenario, orderId: String, reason: String): CancelOrderResult {
        val request = CancelOrderPayload(reason = reason)
        return restClient.post()
            .uri("/api/orders/{orderId}/cancel", orderId)
            .header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue)
            .contentType(APPLICATION_JSON)
            .body(request)
            .exchange { _, response ->
                when (response.statusCode) {
                    OK -> {
                        val body = response.bodyTo(CancelOrderResponsePayload::class.java)
                            ?: throw unexpectedStatus(response)
                        CancelOrderResult.Success(
                            orderId = body.orderId,
                            state = body.state,
                            reason = reason,
                        )
                    }
                    else -> throw unexpectedStatus(response)
                }
            }
    }

    override fun getOrder(scenario: CheckoutScenario, orderId: String): OrderDetails = restClient.get()
        .uri("/api/orders/{orderId}", orderId)
        .header(HEADER_CHECKOUT_SCENARIO, scenario.headerValue)
        .exchange { _, response ->
            when (response.statusCode) {
                OK -> {
                    val body = response.bodyTo(GetOrderResponsePayload::class.java)
                        ?: throw unexpectedStatus(response)
                    OrderDetails(
                        orderId = body.orderId,
                        customerId = body.customerId,
                        state = body.state,
                        totalAmount = body.totalAmount.toDouble(),
                        currency = body.currency,
                        itemCount = body.items.size.toLong(),
                    )
                }
                else -> throw unexpectedStatus(response)
            }
        }

    private fun unexpectedStatus(
        response: org.springframework.http.client.ClientHttpResponse,
    ): RestClientResponseException = RestClientResponseException(
        "Unexpected HTTP status: ${response.statusCode}",
        response.statusCode,
        response.statusText,
        response.headers,
        response.body.readAllBytes(),
        StandardCharsets.UTF_8,
    )

    private companion object {
        const val HEADER_CHECKOUT_SCENARIO = "X-Checkout-Scenario"
    }
}

private data class CreateOrderPayload(val customerId: String, val items: List<CreateOrderLineItemPayload>)

private data class CreateOrderLineItemPayload(
    val sku: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val currency: String,
)

private data class CreateOrderResponsePayload(val orderId: String, val state: String)

private data class ReserveOrderResponsePayload(val orderId: String, val state: String)

private data class PayOrderPayload(val paymentMethod: String)

private data class PayOrderResponsePayload(val orderId: String, val state: String)

private data class ShipOrderPayload(val shippingMethod: String)

private data class ShipOrderResponsePayload(val orderId: String, val state: String)

private data class CancelOrderPayload(val reason: String)

private data class CancelOrderResponsePayload(val orderId: String, val state: String)

private data class GetOrderResponsePayload(
    val orderId: String,
    val customerId: String,
    val state: String,
    val totalAmount: BigDecimal,
    val currency: String,
    val items: List<GetOrderItemPayload> = emptyList(),
)

private data class GetOrderItemPayload(
    val sku: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val currency: String,
)
