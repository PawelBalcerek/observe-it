package com.observeit.observedapp.order

import com.observeit.observedapp.order.domain.PaymentDeclinedException
import com.observeit.observedapp.order.domain.PaymentGateway
import com.observeit.observedapp.order.domain.PaymentGatewayTimeoutException
import com.observeit.observedapp.order.domain.PaymentMethod
import com.observeit.observedapp.order.domain.PaymentMethod.CREDIT_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.DECLINED_CARD
import com.observeit.observedapp.order.domain.PaymentMethod.TIMEOUT_GATEWAY
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.TimeoutException

class FakePaymentGateway : PaymentGateway {

    override fun processPayment(
        orderId: String,
        amount: BigDecimal,
        currency: Currency,
        paymentMethod: PaymentMethod,
    ) {
        when (paymentMethod) {
            CREDIT_CARD -> {
                // Success: no-op
            }
            DECLINED_CARD -> {
                throw PaymentDeclinedException(paymentMethod)
            }
            TIMEOUT_GATEWAY -> {
                val cause = TimeoutException("Payment gateway timed out after 1500ms")
                throw PaymentGatewayTimeoutException(paymentMethod, latencyMs = 1500L, cause = cause)
            }
        }
    }
}
