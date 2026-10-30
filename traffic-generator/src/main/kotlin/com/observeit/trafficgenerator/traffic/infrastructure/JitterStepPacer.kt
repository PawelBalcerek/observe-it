package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.domain.StepPacer
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit.MILLISECONDS

@Component
class JitterStepPacer(properties: TrafficProperties) : StepPacer {

    private val minJitterMs = properties.workers.minJitterMs
    private val maxJitterMs = properties.workers.maxJitterMs

    override fun pace() {
        if (maxJitterMs <= 0L) {
            return
        }
        val delay = ThreadLocalRandom.current().nextLong(minJitterMs, maxJitterMs + 1)
        val executor = CompletableFuture.delayedExecutor(delay, MILLISECONDS)
        CompletableFuture.runAsync({}, executor).join()
    }
}
