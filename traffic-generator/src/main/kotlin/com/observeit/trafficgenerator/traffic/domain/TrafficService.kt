package com.observeit.trafficgenerator.traffic.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TrafficService(
    private val workerControl: TrafficWorkerControl,
    private val scenarioExecutionCounters: ScenarioExecutionCounters,
    private val scenarioRunner: CheckoutScenarioRunner,
) {

    private val logger = LoggerFactory.getLogger(TrafficService::class.java)

    fun trigger(scenario: CheckoutScenario): ScenarioExecutionResult = scenarioRunner.execute(scenario)

    fun getStatus(): TrafficStatus {
        val running = workerControl.isRunning()
        return TrafficStatus(
            running = running,
            paused = !running,
            ordersPerSecond = workerControl.ordersPerSecond,
            concurrency = workerControl.concurrency,
            scenarioExecutionCounts = scenarioExecutionCounters.getCounts(),
        )
    }

    fun pause(): TrafficStatus {
        workerControl.pause()
        val status = getStatus()
        logger.atInfo()
            .addKeyValue(Key.TRAFFIC_RUNNING, status.running)
            .addKeyValue(Key.TRAFFIC_PAUSED, status.paused)
            .log(Message.TRAFFIC_PAUSED)
        return status
    }

    fun resume(): TrafficStatus {
        workerControl.resume()
        val status = getStatus()
        logger.atInfo()
            .addKeyValue(Key.TRAFFIC_RUNNING, status.running)
            .addKeyValue(Key.TRAFFIC_PAUSED, status.paused)
            .log(Message.TRAFFIC_RESUMED)
        return status
    }

    fun updateRate(update: TrafficRateUpdate): TrafficRate {
        val updatedRate = workerControl.updateRate(update)
        logger.atInfo()
            .addKeyValue(Key.TRAFFIC_ORDERS_PER_SECOND, updatedRate.ordersPerSecond)
            .addKeyValue(Key.TRAFFIC_CONCURRENCY, updatedRate.concurrency)
            .log(Message.TRAFFIC_RATE_UPDATED)
        return updatedRate
    }

    private companion object {
        object Key {
            const val TRAFFIC_RUNNING = "traffic.running"
            const val TRAFFIC_PAUSED = "traffic.paused"
            const val TRAFFIC_ORDERS_PER_SECOND = "traffic.orders_per_second"
            const val TRAFFIC_CONCURRENCY = "traffic.concurrency"
        }

        object Message {
            const val TRAFFIC_PAUSED = "Traffic generator paused"
            const val TRAFFIC_RESUMED = "Traffic generator resumed"
            const val TRAFFIC_RATE_UPDATED = "Traffic generator rate updated"
        }
    }
}
