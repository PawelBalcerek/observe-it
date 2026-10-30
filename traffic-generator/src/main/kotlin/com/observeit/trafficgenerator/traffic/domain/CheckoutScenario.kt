package com.observeit.trafficgenerator.traffic.domain

@Suppress("MagicNumber")
enum class CheckoutScenario(val probability: Double, val headerValue: String, val sku: String = "sku") {
    HAPPY_PATH(0.55, "HAPPY_PATH"),
    LOW_STOCK_WARNING(0.15, "LOW_STOCK_WARNING", "SKU-LOW-STOCK"),
    OUT_OF_STOCK(0.10, "OUT_OF_STOCK", "SKU-OUT-OF-STOCK"),
    PAYMENT_DECLINED(0.08, "PAYMENT_DECLINED"),
    PAYMENT_GATEWAY_TIMEOUT(0.05, "PAYMENT_GATEWAY_TIMEOUT"),
    BUYER_CANCELLATION(0.05, "BUYER_CANCELLATION"),
    INVALID_STATE_TRANSITION(0.02, "INVALID_STATE_TRANSITION"),
}
