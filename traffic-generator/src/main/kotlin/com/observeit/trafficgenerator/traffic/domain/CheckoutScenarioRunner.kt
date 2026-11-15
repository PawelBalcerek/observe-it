package com.observeit.trafficgenerator.traffic.domain

import com.observeit.trafficgenerator.correlation.CorrelationContext
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.BUYER_CANCELLATION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.INVALID_STATE_TRANSITION
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.LOW_STOCK_WARNING
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.OUT_OF_STOCK
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_DECLINED
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.PAYMENT_GATEWAY_TIMEOUT
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.ErrorType
import com.observeit.trafficgenerator.traffic.domain.TrafficTelemetry.Key
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.measureTimedValue

@Service
class CheckoutScenarioRunner(
    private val client: ObservedAppClient,
    private val stepPacer: StepPacer,
    private val counters: ScenarioExecutionCounters,
    private val trafficMetrics: TrafficMetrics,
    private val trafficTracer: TrafficTracer,
) {

    private val logger = LoggerFactory.getLogger(CheckoutScenarioRunner::class.java)

    fun execute(scenario: CheckoutScenario): ScenarioExecutionResult {
        val customerId = "cust-${UUID.randomUUID().toString().take(CUSTOMER_ID_LENGTH)}"
        CorrelationContext.setCheckoutScenario(scenario.name)
        CorrelationContext.setCustomerId(customerId)

        try {
            return trafficTracer.traceScenario(scenario, customerId) { scenarioSpan ->
                val (finalOrder, duration) = measureTimedValue {
                    val orderId = executeOrderCreation(scenario, customerId, scenario.sku)
                    scenarioSpan.recordOrderCreated(orderId)
                    stepPacer.pace()

                    val stoppedEarly = executeReservation(scenario, orderId, scenario.sku)
                    if (!stoppedEarly) {
                        executeRemainingSteps(scenario, orderId, scenarioSpan)
                    }

                    retrieveFinalOrder(scenario, orderId)
                }
                counters.increment(scenario)
                trafficMetrics.recordScenarioExecuted(scenario, finalOrder.state)
                trafficMetrics.recordScenarioDuration(scenario, duration.toDouble(MILLISECONDS))
                ScenarioExecutionResult(
                    scenario = scenario,
                    orderId = finalOrder.orderId,
                    customerId = customerId,
                    finalState = finalOrder.state,
                    success = true,
                )
            }
        } finally {
            CorrelationContext.clear()
        }
    }

    private fun executeOrderCreation(scenario: CheckoutScenario, customerId: String, sku: String): String {
        val created = client.createOrder(
            scenario = scenario,
            customerId = customerId,
            items = listOf(
                CreateOrderItem(
                    sku = sku,
                    quantity = DEFAULT_ITEM_QUANTITY,
                    unitPrice = DEFAULT_ITEM_PRICE,
                    currency = DEFAULT_CURRENCY,
                ),
            ),
        )
        val orderId = created.orderId
        CorrelationContext.setOrderId(orderId)

        logger.atInfo()
            .addKeyValue(Key.ORDER_ID, orderId)
            .addKeyValue(Key.ORDER_STATE, OrderState.CREATED)
            .log("Order created")

        return orderId
    }

    private fun executeReservation(scenario: CheckoutScenario, orderId: String, sku: String): Boolean =
        when (client.reserveOrder(scenario, orderId)) {
            is ReserveOrderResult.OutOfStock -> {
                logger.atWarn()
                    .addKeyValue(Key.ERROR_TYPE, ErrorType.OUT_OF_STOCK)
                    .addKeyValue(Key.INVENTORY_SKU, sku)
                    .log("Order reservation failed due to out of stock")
                stepPacer.pace()
                true
            }

            is ReserveOrderResult.Success -> {
                logger.atInfo()
                    .addKeyValue(Key.ORDER_STATE, OrderState.RESERVED)
                    .addKeyValue(Key.ORDER_PREVIOUS_STATE, OrderState.CREATED)
                    .log("Order reserved")
                stepPacer.pace()
                false
            }
        }

    private fun executeRemainingSteps(scenario: CheckoutScenario, orderId: String, scenarioSpan: ScenarioSpan) {
        when (scenario) {
            BUYER_CANCELLATION -> executeCancellation(scenario, orderId, CancellationReason.BUYER_CANCELLATION)
            INVALID_STATE_TRANSITION -> executeInvalidTransition(scenario, orderId)
            PAYMENT_DECLINED -> executePaymentDeclined(scenario, orderId)
            PAYMENT_GATEWAY_TIMEOUT -> executePaymentTimeout(scenario, orderId, scenarioSpan)
            HAPPY_PATH, LOW_STOCK_WARNING -> executeHappyOrLowStock(scenario, orderId)
            OUT_OF_STOCK -> Unit
        }
    }

    private fun executeCancellation(scenario: CheckoutScenario, orderId: String, reason: String) {
        val cancelResult = client.cancelOrder(scenario, orderId, reason)
        if (cancelResult is CancelOrderResult.Success) {
            logger.atInfo()
                .addKeyValue(Key.ORDER_STATE, OrderState.CANCELLED)
                .addKeyValue(Key.ORDER_PREVIOUS_STATE, OrderState.RESERVED)
                .addKeyValue(Key.CANCELLATION_REASON, reason)
                .log("Order cancelled")
        }
        stepPacer.pace()
    }

    private fun executeInvalidTransition(scenario: CheckoutScenario, orderId: String) {
        executeCancellation(scenario, orderId, CancellationReason.INVALID_TRANSITION)
        val payResult = client.payOrder(scenario, orderId, PaymentMethod.CREDIT_CARD)
        if (payResult is PayOrderResult.InvalidTransition) {
            logger.atWarn()
                .addKeyValue(Key.ERROR_TYPE, ErrorType.INVALID_STATE_TRANSITION)
                .addKeyValue(Key.ORDER_ATTEMPTED_ACTION, Action.PAY)
                .log("Cannot pay order in CANCELLED state")
        }
        stepPacer.pace()
    }

    private fun executePaymentDeclined(scenario: CheckoutScenario, orderId: String) {
        val payResult = client.payOrder(scenario, orderId, PaymentMethod.DECLINED_CARD)
        if (payResult is PayOrderResult.Declined) {
            logger.atWarn()
                .addKeyValue(Key.ERROR_TYPE, ErrorType.PAYMENT_DECLINED)
                .addKeyValue(Key.PAYMENT_METHOD, PaymentMethod.DECLINED_CARD)
                .log("Payment declined for method DECLINED_CARD")
        }
        stepPacer.pace()
    }

    private fun executePaymentTimeout(scenario: CheckoutScenario, orderId: String, scenarioSpan: ScenarioSpan) {
        val payResult = client.payOrder(scenario, orderId, PaymentMethod.TIMEOUT_GATEWAY)
        if (payResult is PayOrderResult.Timeout) {
            scenarioSpan.recordTimeout()
            logger.atError()
                .addKeyValue(Key.ERROR_TYPE, ErrorType.GATEWAY_TIMEOUT)
                .addKeyValue(Key.PAYMENT_METHOD, PaymentMethod.TIMEOUT_GATEWAY)
                .log("Payment gateway timed out")
        }
        stepPacer.pace()
    }

    private fun executeHappyOrLowStock(scenario: CheckoutScenario, orderId: String) {
        val payResult = client.payOrder(scenario, orderId, PaymentMethod.CREDIT_CARD)
        if (payResult is PayOrderResult.Success) {
            logger.atInfo()
                .addKeyValue(Key.ORDER_STATE, OrderState.PAID)
                .addKeyValue(Key.ORDER_PREVIOUS_STATE, OrderState.RESERVED)
                .addKeyValue(Key.PAYMENT_METHOD, PaymentMethod.CREDIT_CARD)
                .log("Order paid")
        }
        stepPacer.pace()

        val shipResult = client.shipOrder(scenario, orderId, ShippingMethod.STANDARD)
        if (shipResult is ShipOrderResult.Success) {
            logger.atInfo()
                .addKeyValue(Key.ORDER_STATE, OrderState.SHIPPED)
                .addKeyValue(Key.ORDER_PREVIOUS_STATE, OrderState.PAID)
                .addKeyValue(Key.SHIPPING_METHOD, ShippingMethod.STANDARD)
                .log("Order shipped")
        }
        stepPacer.pace()
    }

    private fun retrieveFinalOrder(scenario: CheckoutScenario, orderId: String): OrderDetails {
        val order = client.getOrder(scenario, orderId)
        logger.atInfo()
            .addKeyValue(Key.ORDER_STATE, order.state)
            .addKeyValue(Key.ORDER_TOTAL_AMOUNT, order.totalAmount)
            .addKeyValue(Key.ORDER_CURRENCY, order.currency)
            .addKeyValue(Key.ORDER_ITEM_COUNT, order.itemCount)
            .log("Order retrieved")
        return order
    }

    private companion object {
        object OrderState {
            const val CREATED = "CREATED"
            const val RESERVED = "RESERVED"
            const val PAID = "PAID"
            const val SHIPPED = "SHIPPED"
            const val CANCELLED = "CANCELLED"
        }

        object Action {
            const val PAY = "pay"
        }

        object PaymentMethod {
            const val CREDIT_CARD = "CREDIT_CARD"
            const val DECLINED_CARD = "DECLINED_CARD"
            const val TIMEOUT_GATEWAY = "TIMEOUT_GATEWAY"
        }

        object ShippingMethod {
            const val STANDARD = "STANDARD"
        }

        object CancellationReason {
            const val BUYER_CANCELLATION = "buyer cancellation request"
            const val INVALID_TRANSITION = "cancelling before invalid pay"
        }

        const val CUSTOMER_ID_LENGTH = 8
        const val DEFAULT_ITEM_QUANTITY = 1
        val DEFAULT_ITEM_PRICE: BigDecimal = BigDecimal("10.00")
        const val DEFAULT_CURRENCY = "USD"
    }
}
