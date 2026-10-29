package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.domain.PaymentDeclinedException
import com.observeit.observedapp.order.domain.PaymentGateway
import com.observeit.observedapp.order.domain.PaymentGatewayTimeoutException
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import kotlin.time.measureTime

@Component
class SimulatedPaymentGateway(private val timeoutDelayMs: Long = GATEWAY_TIMEOUT_DELAY_MS) : PaymentGateway {

    override fun processPayment(
        orderId: String,
        amount: BigDecimal,
        currency: Currency,
        paymentMethod: PaymentMethod,
    ) {
        when (paymentMethod) {
            CREDIT_CARD -> {
                // Success: simulate normal processing
            }

            DECLINED_CARD -> {
                throw PaymentDeclinedException(paymentMethod)
            }

            TIMEOUT_GATEWAY -> {
                val elapsed = measureTime {
                    val executor = CompletableFuture.delayedExecutor(timeoutDelayMs, MILLISECONDS)
                    CompletableFuture.runAsync({}, executor).join()
                }
                val latencyMs = elapsed.inWholeMilliseconds
                val cause = TimeoutException("Payment gateway timed out after ${latencyMs}ms")
                throw PaymentGatewayTimeoutException(paymentMethod, latencyMs, cause)
            }
        }
    }

    private companion object {
        const val GATEWAY_TIMEOUT_DELAY_MS = 1500L
    }
}
