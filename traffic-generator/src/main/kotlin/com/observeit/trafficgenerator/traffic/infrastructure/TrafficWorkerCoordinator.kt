package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenarioRunner
import com.observeit.trafficgenerator.traffic.domain.ScenarioSelector
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates concurrent background traffic generator workers executing checkout scenarios.
 *
 * Architectural decisions:
 * 1. **Spring [SmartLifecycle]**
 *    (https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html#beans-factory-lifecycle):
 *    Manages worker startup and teardown bound to the Spring application context lifecycle. Configured with phase
 *    [Integer.MAX_VALUE] so workers start only after all other beans are initialized and stop first on shutdown.
 * 2. **Java Virtual Threads (JEP 444, https://openjdk.org/jeps/444)**: Workers run as lightweight virtual threads via
 *    [Thread.ofVirtual], parking cheaply during outbound HTTP calls and inter-step delays.
 * 3. **Non-blocking Delay**
 *    (https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html):
 *    Uses [CompletableFuture.delayedExecutor] for inter-iteration pauses rather than `Thread.sleep`.
 * 4. **Cancellation**: Responds to thread interrupts during shutdown via [AtomicBoolean] state checks and
 *    [InterruptedException] handling.
 */
@Component
class TrafficWorkerCoordinator(
    private val runner: CheckoutScenarioRunner,
    private val selector: ScenarioSelector,
    private val properties: TrafficProperties,
    private val iterationDelayMs: Long = DEFAULT_ITERATION_DELAY_MS,
) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(TrafficWorkerCoordinator::class.java)
    private val running = AtomicBoolean(false)
    private val workerThreads = CopyOnWriteArrayList<Thread>()

    override fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        val concurrency = properties.workers.concurrency
        logger.info("Starting {} virtual-thread traffic generator workers", concurrency)

        for (i in 1..concurrency) {
            val thread = Thread.ofVirtual()
                .name("traffic-worker-$i")
                .start { runWorkerLoop() }
            workerThreads.add(thread)
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        logger.info("Stopping traffic generator workers")
        for (thread in workerThreads) {
            thread.interrupt()
        }
        workerThreads.clear()
    }

    override fun isRunning(): Boolean = running.get()

    override fun isAutoStartup(): Boolean = properties.workers.enabled

    override fun getPhase(): Int = Integer.MAX_VALUE

    private fun runWorkerLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val shouldContinue = runSingleIteration()
            if (!shouldContinue) {
                break
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runSingleIteration(): Boolean = try {
        val scenario = selector.select()
        runner.execute(scenario)
        delayIteration()
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    } catch (e: CompletionException) {
        if (e.cause is InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } else {
            logger.warn("Worker error during iteration delay: {}", e.message)
            true
        }
    } catch (e: Exception) {
        logger.warn("Worker encountered error in loop: {}", e.message)
        delayIteration()
        true
    }

    private fun delayIteration() {
        try {
            if (iterationDelayMs > 0L) {
                val executor = CompletableFuture.delayedExecutor(iterationDelayMs, MILLISECONDS)
                CompletableFuture.runAsync({}, executor).join()
            }
        } catch (_: Exception) {
            // Ignored on interruption or cancellation during shutdown
        }
    }

    private companion object {
        const val DEFAULT_ITERATION_DELAY_MS = 500L
    }
}
