package com.observeit.trafficgenerator.traffic.api

import com.observeit.trafficgenerator.traffic.domain.TrafficRate

data class UpdateTrafficRateResponse(val ordersPerSecond: Double, val concurrency: Int) {
    companion object {
        fun from(rate: TrafficRate): UpdateTrafficRateResponse = UpdateTrafficRateResponse(
            ordersPerSecond = rate.ordersPerSecond,
            concurrency = rate.concurrency,
        )
    }
}
