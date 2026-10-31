package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenarioRunner
import com.observeit.trafficgenerator.traffic.domain.ScenarioSelector
import com.observeit.trafficgenerator.traffic.domain.TrafficWorkerControl
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
 * 3. **Lock-Free Concurrency State Machine**: Uses an [AtomicReference] over [CoordinatorState] ([Stopped],
 *    [Starting], [Running], [Stopping]) with monotonic generation sequencing and a bounded [WorkerSession] to prevent
 *    overlapping startup loops, ABA race hazards, and worker concurrency oversubscription under rapid toggle.
 * 4. **Interruptible Delay**
 *    (https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html):
 *    Uses [CompletableFuture.delayedExecutor] and [CompletableFuture.get] for inter-iteration pauses rather than
 *    `Thread.sleep`, promptly responding to interrupts during shutdown or pause.
 * 5. **Cancellation & Bounded Join**: Responds to thread interrupts during shutdown via [WorkerSession] scoped state
 *    checks, interrupt cause detection, and bounded worker thread joining with [STOP_JOIN_TIMEOUT_MS].
 */
@Component
class TrafficWorkerCoordinator(
    private val runner: CheckoutScenarioRunner,
    private val selector: ScenarioSelector,
    private val properties: TrafficProperties,
    private val iterationDelayMs: Long = DEFAULT_ITERATION_DELAY_MS,
) : SmartLifecycle,
    TrafficWorkerControl {

    private val logger = LoggerFactory.getLogger(TrafficWorkerCoordinator::class.java)
    private val generationSequence = AtomicLong(0L)
    private val state = AtomicReference<CoordinatorState>(CoordinatorState.Stopped)

    override val concurrency: Int
        get() = properties.workers.concurrency

    override val ordersPerSecond: Double
        get() = properties.ordersPerSecond

    override fun start() {
        var started = false
        while (!started) {
            when (val current = state.get()) {
                is CoordinatorState.Running,
                is CoordinatorState.Starting,
                -> started = true

                is CoordinatorState.Stopping -> {
                    current.session.awaitTermination()
                    Thread.yield()
                }

                is CoordinatorState.Stopped -> {
                    val nextGeneration = generationSequence.incrementAndGet()
                    val session = WorkerSession(nextGeneration)
                    val starting = CoordinatorState.Starting(nextGeneration, session)
                    if (state.compareAndSet(current, starting)) {
                        launchWorkers(starting)
                        started = true
                    }
                }
            }
        }
    }

    override fun stop() {
        var stopped = false
        while (!stopped) {
            when (val current = state.get()) {
                is CoordinatorState.Stopped -> {
                    stopped = true
                }

                is CoordinatorState.Stopping -> {
                    current.session.awaitTermination()
                    stopped = true
                }

                is CoordinatorState.Starting -> {
                    val stoppingState = CoordinatorState.Stopping(current.generation, current.session)
                    if (state.compareAndSet(current, stoppingState)) {
                        logger.info("Stopping traffic generator workers")
                        current.session.stop()
                        state.compareAndSet(stoppingState, CoordinatorState.Stopped)
                        stopped = true
                    }
                }

                is CoordinatorState.Running -> {
                    val stoppingState = CoordinatorState.Stopping(current.generation, current.session)
                    if (state.compareAndSet(current, stoppingState)) {
                        logger.info("Stopping traffic generator workers")
                        current.session.stop()
                        state.compareAndSet(stoppingState, CoordinatorState.Stopped)
                        stopped = true
                    }
                }
            }
        }
    }

    override fun pause() {
        stop()
    }

    override fun resume() {
        start()
    }

    override fun isRunning(): Boolean = state.get().isRunning

    override fun isAutoStartup(): Boolean = properties.workers.enabled

    override fun getPhase(): Int = Integer.MAX_VALUE

    private fun launchWorkers(starting: CoordinatorState.Starting) {
        val session = starting.session
        val concurrency = properties.workers.concurrency
        logger.info("Starting {} virtual-thread traffic generator workers", concurrency)

        for (i in 1..concurrency) {
            val shouldStart = session.isRunning.get() && state.get() === starting
            if (!shouldStart) {
                break
            }
            val thread = Thread.ofVirtual()
                .name("traffic-worker-$i")
                .unstarted { runWorkerLoop(session) }
            session.threads.add(thread)
            if (session.isRunning.get() && state.get() === starting) {
                thread.start()
            } else {
                thread.interrupt()
            }
        }

        val runningState = CoordinatorState.Running(starting.generation, session)
        if (!state.compareAndSet(starting, runningState)) {
            session.stop()
        }
    }

    private fun runWorkerLoop(session: WorkerSession) {
        while (session.isRunning.get() && !Thread.currentThread().isInterrupted) {
            val shouldContinue = runSingleIteration(session)
            if (!shouldContinue) {
                break
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runSingleIteration(session: WorkerSession): Boolean = try {
        if (!session.isRunning.get() || Thread.currentThread().isInterrupted) {
            false
        } else {
            val scenario = selector.select()
            if (!session.isRunning.get() || Thread.currentThread().isInterrupted) {
                false
            } else {
                runner.execute(scenario)
                delayIteration(session)
                session.isRunning.get() && !Thread.currentThread().isInterrupted
            }
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    } catch (e: Exception) {
        if (!session.isRunning.get() || Thread.currentThread().isInterrupted || isInterruptedCause(e)) {
            Thread.currentThread().interrupt()
            false
        } else {
            logger.warn("Worker encountered error in loop: {}", e.message)
            delayIteration(session)
            session.isRunning.get() && !Thread.currentThread().isInterrupted
        }
    }

    private fun delayIteration(session: WorkerSession) {
        if (!session.isRunning.get() || Thread.currentThread().isInterrupted) {
            return
        }
        if (iterationDelayMs > 0L) {
            val executor = CompletableFuture.delayedExecutor(iterationDelayMs, MILLISECONDS)
            val future = CompletableFuture.runAsync({}, executor)
            try {
                future.get()
            } catch (_: InterruptedException) {
                future.cancel(true)
                Thread.currentThread().interrupt()
            } catch (_: ExecutionException) {
                // Empty async task cannot throw execution exception
            }
        }
    }

    private sealed interface CoordinatorState {
        val isRunning: Boolean

        data object Stopped : CoordinatorState {
            override val isRunning: Boolean = false
        }

        data class Starting(val generation: Long, val session: WorkerSession) : CoordinatorState {
            override val isRunning: Boolean = true
        }

        data class Running(val generation: Long, val session: WorkerSession) : CoordinatorState {
            override val isRunning: Boolean = true
        }

        data class Stopping(val generation: Long, val session: WorkerSession) : CoordinatorState {
            override val isRunning: Boolean = false
        }
    }

    private class WorkerSession(val generation: Long) {
        val isRunning = AtomicBoolean(true)
        val threads = CopyOnWriteArrayList<Thread>()
        private val terminationLatch = CountDownLatch(1)

        fun stop() {
            if (isRunning.compareAndSet(true, false)) {
                for (thread in threads) {
                    thread.interrupt()
                }
                for (thread in threads) {
                    if (thread !== Thread.currentThread() && thread.isAlive) {
                        try {
                            thread.join(STOP_JOIN_TIMEOUT_MS)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
                threads.clear()
                terminationLatch.countDown()
            } else {
                awaitTermination()
            }
        }

        fun awaitTermination(timeoutMs: Long = STOP_JOIN_TIMEOUT_MS): Boolean = try {
            terminationLatch.await(timeoutMs, MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private companion object {
        const val DEFAULT_ITERATION_DELAY_MS = 500L
        const val STOP_JOIN_TIMEOUT_MS = 1000L

        fun isInterruptedCause(e: Throwable): Boolean {
            var current: Throwable? = e
            while (current != null) {
                if (current is InterruptedException ||
                    current is InterruptedIOException ||
                    current is CancellationException
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }
    }
}
