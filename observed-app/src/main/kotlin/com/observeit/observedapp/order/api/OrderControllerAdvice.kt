package com.observeit.observedapp.order.api

import com.observeit.observedapp.order.domain.InvalidOrderStateTransitionException
import com.observeit.observedapp.order.domain.OrderTelemetry.ErrorType
import com.observeit.observedapp.order.domain.OrderTelemetry.Key
import com.observeit.observedapp.order.domain.OutOfStockException
import com.observeit.observedapp.order.domain.PaymentDeclinedException
import com.observeit.observedapp.order.domain.PaymentGatewayTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.GATEWAY_TIMEOUT
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(HIGHEST_PRECEDENCE)
class OrderControllerAdvice {

    private val logger = LoggerFactory.getLogger(OrderControllerAdvice::class.java)

    @ExceptionHandler(InvalidOrderStateTransitionException::class)
    fun handleInvalidOrderStateTransitionException(exception: InvalidOrderStateTransitionException): ProblemDetail {
        logger.atWarn()
            .addKeyValue(Key.ERROR_TYPE, ErrorType.INVALID_STATE_TRANSITION)
            .addKeyValue(Key.ORDER_ATTEMPTED_ACTION, exception.attemptedAction)
            .log("Cannot ${exception.attemptedAction} order in ${exception.state} state")

        val detail = exception.message ?: CONFLICT.reasonPhrase
        return problemDetail(CONFLICT, detail)
    }

    @ExceptionHandler(OutOfStockException::class)
    fun handleOutOfStockException(exception: OutOfStockException): ProblemDetail {
        logger.atWarn()
            .addKeyValue(Key.ERROR_TYPE, ErrorType.OUT_OF_STOCK)
            .addKeyValue(Key.INVENTORY_SKU, exception.sku)
            .addKeyValue(Key.INVENTORY_REQUESTED_QUANTITY, exception.requestedQuantity)
            .addKeyValue(Key.INVENTORY_AVAILABLE_QUANTITY, exception.availableQuantity)
            .log("Inventory out of stock for ${exception.sku}")

        val detail = exception.message ?: UNPROCESSABLE_CONTENT.reasonPhrase
        return problemDetail(UNPROCESSABLE_CONTENT, detail)
    }

    @ExceptionHandler(PaymentDeclinedException::class)
    fun handlePaymentDeclinedException(exception: PaymentDeclinedException): ProblemDetail {
        logger.atWarn()
            .addKeyValue(Key.ERROR_TYPE, ErrorType.PAYMENT_DECLINED)
            .addKeyValue(Key.PAYMENT_METHOD, exception.paymentMethod.name)
            .addKeyValue(Key.PAYMENT_LATENCY_MS, exception.latencyMs)
            .log("Payment declined for method ${exception.paymentMethod.name}")

        val detail = exception.message ?: UNPROCESSABLE_CONTENT.reasonPhrase
        return problemDetail(UNPROCESSABLE_CONTENT, detail)
    }

    @ExceptionHandler(PaymentGatewayTimeoutException::class)
    fun handlePaymentGatewayTimeoutException(exception: PaymentGatewayTimeoutException): ProblemDetail {
        logger.atError()
            .setCause(exception.cause ?: exception)
            .addKeyValue(Key.ERROR_TYPE, ErrorType.GATEWAY_TIMEOUT)
            .addKeyValue(Key.PAYMENT_METHOD, exception.paymentMethod.name)
            .addKeyValue(Key.PAYMENT_LATENCY_MS, exception.latencyMs)
            .log("Payment gateway timed out for method ${exception.paymentMethod.name}")

        val detail = exception.message
        return problemDetail(GATEWAY_TIMEOUT, detail)
    }

    private fun problemDetail(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail)
}
