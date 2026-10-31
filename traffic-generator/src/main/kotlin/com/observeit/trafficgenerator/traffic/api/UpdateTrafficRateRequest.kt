package com.observeit.trafficgenerator.traffic.api

import com.observeit.trafficgenerator.traffic.domain.TrafficRateUpdate
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Positive

data class UpdateTrafficRateRequest(
    @field:Positive(message = "ordersPerSecond must be greater than 0")
    val ordersPerSecond: Double? = null,
    @field:Positive(message = "concurrency must be greater than 0")
    val concurrency: Int? = null,
) {
    @AssertTrue(message = "At least one of ordersPerSecond or concurrency must be provided")
    fun hasAtLeastOneUpdate(): Boolean = ordersPerSecond != null || concurrency != null

    fun toDomain(): TrafficRateUpdate = TrafficRateUpdate(
        ordersPerSecond = ordersPerSecond,
        concurrency = concurrency,
    )
}
