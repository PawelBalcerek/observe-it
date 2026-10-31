package com.observeit.trafficgenerator.traffic.domain

interface TrafficWorkerControl {
    fun isRunning(): Boolean
    val concurrency: Int
    val ordersPerSecond: Double

    fun pause()
    fun resume()
    fun updateRate(update: TrafficRateUpdate): TrafficRate
}
