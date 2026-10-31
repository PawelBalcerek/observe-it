package com.observeit.trafficgenerator.traffic.infrastructure

import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTrafficRate
import com.observeit.trafficgenerator.traffic.domain.CancelOrderResult
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario.HAPPY_PATH
import com.observeit.trafficgenerator.traffic.domain.CheckoutScenarioRunner
import com.observeit.trafficgenerator.traffic.domain.CreateOrderItem
import com.observeit.trafficgenerator.traffic.domain.CreateOrderResult
import com.observeit.trafficgenerator.traffic.domain.NoOpStepPacer
import com.observeit.trafficgenerator.traffic.domain.ObservedAppClient
import com.observeit.trafficgenerator.traffic.domain.OrderDetails
import com.observeit.trafficgenerator.traffic.domain.PayOrderResult
import com.observeit.trafficgenerator.traffic.domain.ReserveOrderResult
import com.observeit.trafficgenerator.traffic.domain.ScenarioSelector
import com.observeit.trafficgenerator.traffic.domain.ShipOrderResult
import com.observeit.trafficgenerator.traffic.domain.TrafficRateUpdate
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.math.max

class TrafficWorkerCoordinatorTest {

    private var coordinator: TrafficWorkerCoordinator? = null

    @AfterEach
    fun tearDown() {
        coordinator?.stop()
    }

    @Test
    fun `starts virtual thread workers and executes scenarios continuously`() {
        // given
        val workerThreads = ConcurrentHashMap.newKeySet<String>()
        val virtualFlags = ConcurrentHashMap.newKeySet<Boolean>()
        val executionCount = AtomicInteger(0)

        val runner = CheckoutScenarioRunner(
            client = recordingClient {
                val currentThread = Thread.currentThread()
                workerThreads.add(currentThread.name)
                virtualFlags.add(currentThread.isVirtual)
                executionCount.incrementAndGet()
            },
            stepPacer = NoOpStepPacer,
        )

        val selector = ScenarioSelector { HAPPY_PATH }

        val properties = TrafficProperties(
            workers = TrafficProperties.WorkersProperties(
                enabled = true,
                concurrency = 3,
                minJitterMs = 1,
                maxJitterMs = 5,
            ),
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = selector,
            properties = properties,
            iterationDelayMs = 1L,
        )

        // when
        coordinator!!.start()

        // then
        await atMost Duration.ofSeconds(2) untilAsserted {
            executionCount.get() shouldBeGreaterThanOrEqualTo 5
            workerThreads.size shouldBe 3
            virtualFlags shouldContainExactlyInAnyOrder listOf(true)
            coordinator!!.isRunning shouldBe true
        }

        // and - clean stop
        coordinator!!.stop()
        coordinator!!.isRunning shouldBe false
    }

    @Test
    fun `worker loops survive exceptions thrown during scenario execution`() {
        // given
        val executionAttempts = AtomicInteger(0)

        val runner = CheckoutScenarioRunner(
            client = recordingClient {
                if (executionAttempts.incrementAndGet() % 2 == 1) {
                    error("Simulated transient failure")
                }
            },
            stepPacer = NoOpStepPacer,
        )

        val selector = ScenarioSelector { HAPPY_PATH }

        val properties = TrafficProperties(
            workers = TrafficProperties.WorkersProperties(
                enabled = true,
                concurrency = 2,
                minJitterMs = 1,
                maxJitterMs = 5,
            ),
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = selector,
            properties = properties,
            iterationDelayMs = 1L,
        )

        // when
        coordinator!!.start()

        // then - continues executing despite exceptions
        await atMost Duration.ofSeconds(2) untilAsserted {
            executionAttempts.get() shouldBeGreaterThanOrEqualTo 6
            coordinator!!.isRunning shouldBe true
        }
    }

    @Test
    fun `pauses running workers and halts scenario execution`() {
        // given
        val executionCount = AtomicInteger(0)
        val runner = CheckoutScenarioRunner(
            client = recordingClient { executionCount.incrementAndGet() },
            stepPacer = NoOpStepPacer,
        )
        val selector = ScenarioSelector { HAPPY_PATH }
        val properties = TrafficProperties(
            workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 2),
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = selector,
            properties = properties,
            iterationDelayMs = 1L,
        )
        coordinator!!.start()

        await atMost Duration.ofSeconds(2) untilAsserted {
            executionCount.get() shouldBeGreaterThanOrEqualTo 4
            coordinator!!.isRunning shouldBe true
        }

        // when
        coordinator!!.pause()

        // then
        coordinator!!.isRunning shouldBe false
        val countAfterPause = executionCount.get()

        // and - verify count remains static after pause
        await atMost Duration.ofMillis(300) untilAsserted {
            executionCount.get() shouldBe countAfterPause
        }
    }

    @Test
    fun `resumes paused workers and continues execution`() {
        // given
        val executionCount = AtomicInteger(0)
        val runner = CheckoutScenarioRunner(
            client = recordingClient { executionCount.incrementAndGet() },
            stepPacer = NoOpStepPacer,
        )
        val selector = ScenarioSelector { HAPPY_PATH }
        val properties = TrafficProperties(
            workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 2),
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = selector,
            properties = properties,
            iterationDelayMs = 1L,
        )
        coordinator!!.start()
        coordinator!!.pause()
        coordinator!!.isRunning shouldBe false
        val countAtPause = executionCount.get()

        // when
        coordinator!!.resume()

        // then
        coordinator!!.isRunning shouldBe true
        await atMost Duration.ofSeconds(2) untilAsserted {
            executionCount.get() shouldBeGreaterThanOrEqualTo countAtPause + 4
        }
    }

    @Test
    fun `pause is idempotent when coordinator is already stopped`() {
        // given
        val runner = CheckoutScenarioRunner(
            client = recordingClient {},
            stepPacer = NoOpStepPacer,
        )
        val selector = ScenarioSelector { HAPPY_PATH }
        val properties = TrafficProperties(
            workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 1),
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = selector,
            properties = properties,
            iterationDelayMs = 1L,
        )

        // when
        coordinator!!.pause()

        // then
        coordinator!!.isRunning shouldBe false
    }

    @Test
    fun `resume is idempotent when coordinator is already running`() {
        // given
        val runner = CheckoutScenarioRunner(
            client = recordingClient {},
            stepPacer = NoOpStepPacer,
        )
        val selector = ScenarioSelector { HAPPY_PATH }
        val properties = TrafficProperties(
            workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 2),
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = selector,
            properties = properties,
            iterationDelayMs = 1L,
        )
        coordinator!!.start()
        coordinator!!.isRunning shouldBe true

        // when
        coordinator!!.resume()

        // then
        coordinator!!.isRunning shouldBe true
    }

    @Test
    fun `rapid concurrent toggle never exceeds configured concurrency`() {
        // given
        val configuredConcurrency = 3
        val activeWorkers = AtomicInteger(0)
        val peakConcurrency = AtomicInteger(0)

        val runner = CheckoutScenarioRunner(
            client = recordingClient {
                val current = activeWorkers.incrementAndGet()
                peakConcurrency.updateAndGet { peak -> max(peak, current) }
                try {
                    CountDownLatch(1).await(SCENARIO_HOLD_DURATION_MS, MILLISECONDS)
                } finally {
                    activeWorkers.decrementAndGet()
                }
            },
            stepPacer = NoOpStepPacer,
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = ScenarioSelector { HAPPY_PATH },
            properties = TrafficProperties(
                workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = configuredConcurrency),
            ),
            iterationDelayMs = 1L,
        )

        val toggleThreads = 8
        val iterationsPerThread = 40
        val completionLatch = CountDownLatch(toggleThreads)

        // when
        for (t in 1..toggleThreads) {
            Thread.ofVirtual().name("toggle-worker-$t").start {
                try {
                    for (i in 1..iterationsPerThread) {
                        if (i % 2 == 0) {
                            coordinator!!.pause()
                        } else {
                            coordinator!!.resume()
                        }
                        if (t % 2 == 0) {
                            LockSupport.parkNanos(TOGGLE_INTERVAL_NANOS)
                        }
                    }
                } finally {
                    completionLatch.countDown()
                }
            }
        }

        val completed = completionLatch.await(5, SECONDS)
        completed shouldBe true

        coordinator!!.pause()

        // then
        await atMost Duration.ofSeconds(2) untilAsserted {
            activeWorkers.get() shouldBe 0
            coordinator!!.isRunning shouldBe false
        }

        // and
        peakConcurrency.get() shouldBeLessThanOrEqualTo configuredConcurrency
        peakConcurrency.get() shouldBeGreaterThanOrEqualTo 1
    }

    @Test
    fun `stop during startup cancels cleanly without leaking active workers`() {
        // given
        val activeWorkers = AtomicInteger(0)
        val runner = CheckoutScenarioRunner(
            client = recordingClient {
                activeWorkers.incrementAndGet()
                try {
                    CountDownLatch(1).await(STARTUP_CANCEL_HOLD_MS, MILLISECONDS)
                } finally {
                    activeWorkers.decrementAndGet()
                }
            },
            stepPacer = NoOpStepPacer,
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = ScenarioSelector { HAPPY_PATH },
            properties = TrafficProperties(
                workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 4),
            ),
            iterationDelayMs = 1L,
        )

        // when
        coordinator!!.start()
        coordinator!!.stop()

        // then
        await atMost Duration.ofSeconds(2) untilAsserted {
            activeWorkers.get() shouldBe 0
            coordinator!!.isRunning shouldBe false
        }
    }

    @Test
    fun `updateRate updates target ordersPerSecond and concurrency when stopped`() {
        // given
        val runner = CheckoutScenarioRunner(
            client = recordingClient {},
            stepPacer = NoOpStepPacer,
        )
        val properties = TrafficProperties(
            ordersPerSecond = 1.0,
            workers = TrafficProperties.WorkersProperties(enabled = false, concurrency = 2),
        )
        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = ScenarioSelector { HAPPY_PATH },
            properties = properties,
            iterationDelayMs = 1L,
        )

        // when
        val updated = coordinator!!.updateRate(TrafficRateUpdate(ordersPerSecond = 4.5, concurrency = 6))

        // then
        updated shouldBe aTrafficRate(ordersPerSecond = 4.5, concurrency = 6)

        // and
        coordinator!!.ordersPerSecond shouldBe 4.5
        coordinator!!.concurrency shouldBe 6
    }

    @Test
    fun `updateRate updates target concurrency dynamically and restarts active workers when running`() {
        // given
        val workerThreads = ConcurrentHashMap.newKeySet<String>()
        val runner = CheckoutScenarioRunner(
            client = recordingClient {
                workerThreads.add(Thread.currentThread().name)
            },
            stepPacer = NoOpStepPacer,
        )
        val properties = TrafficProperties(
            ordersPerSecond = 1.0,
            workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 2),
        )
        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = ScenarioSelector { HAPPY_PATH },
            properties = properties,
            iterationDelayMs = 1L,
        )
        coordinator!!.start()

        await atMost Duration.ofSeconds(2) untilAsserted {
            workerThreads shouldContainExactlyInAnyOrder setOf("traffic-worker-1", "traffic-worker-2")
        }

        // when
        val updated = coordinator!!.updateRate(TrafficRateUpdate(ordersPerSecond = 5.0, concurrency = 4))

        // then
        updated shouldBe aTrafficRate(ordersPerSecond = 5.0, concurrency = 4)

        // and
        await atMost Duration.ofSeconds(2) untilAsserted {
            workerThreads shouldContainExactlyInAnyOrder setOf(
                "traffic-worker-1",
                "traffic-worker-2",
                "traffic-worker-3",
                "traffic-worker-4",
            )
            coordinator!!.isRunning shouldBe true
            coordinator!!.ordersPerSecond shouldBe 5.0
            coordinator!!.concurrency shouldBe 4
        }
    }

    @Test
    fun `updateRate preserves existing concurrency when only ordersPerSecond is updated`() {
        // given
        val runner = CheckoutScenarioRunner(
            client = recordingClient {},
            stepPacer = NoOpStepPacer,
        )
        val properties = TrafficProperties(
            ordersPerSecond = 1.0,
            workers = TrafficProperties.WorkersProperties(enabled = false, concurrency = 3),
        )
        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = ScenarioSelector { HAPPY_PATH },
            properties = properties,
            iterationDelayMs = 1L,
        )

        // when
        val updated = coordinator!!.updateRate(TrafficRateUpdate(ordersPerSecond = 8.0))

        // then
        updated shouldBe aTrafficRate(ordersPerSecond = 8.0, concurrency = 3)

        // and
        coordinator!!.ordersPerSecond shouldBe 8.0
        coordinator!!.concurrency shouldBe 3
    }

    @Test
    fun `rapid rate updates maintain consistent state and bounds active threads`() {
        // given
        val activeWorkers = AtomicInteger(0)
        val peakWorkers = AtomicInteger(0)

        val runner = CheckoutScenarioRunner(
            client = recordingClient {
                val current = activeWorkers.incrementAndGet()
                peakWorkers.accumulateAndGet(current, ::max)
                LockSupport.parkNanos(SCENARIO_HOLD_DURATION_MS * 1_000_000)
                activeWorkers.decrementAndGet()
            },
            stepPacer = NoOpStepPacer,
        )

        coordinator = TrafficWorkerCoordinator(
            runner = runner,
            selector = ScenarioSelector { HAPPY_PATH },
            properties = TrafficProperties(
                workers = TrafficProperties.WorkersProperties(enabled = true, concurrency = 2),
            ),
            iterationDelayMs = 1L,
        )

        coordinator!!.start()

        // when
        for (i in 1..20) {
            val targetConcurrency = if (i % 2 == 0) 3 else 1
            coordinator!!.updateRate(
                TrafficRateUpdate(ordersPerSecond = i.toDouble(), concurrency = targetConcurrency),
            )
            LockSupport.parkNanos(TOGGLE_INTERVAL_NANOS)
        }

        // then
        await atMost Duration.ofSeconds(3) untilAsserted {
            coordinator!!.isRunning shouldBe true
            coordinator!!.concurrency shouldBe 3
            coordinator!!.ordersPerSecond shouldBe 20.0
            peakWorkers.get() shouldBeLessThanOrEqualTo 5
        }
    }

    private fun recordingClient(onCreateOrder: () -> Unit): ObservedAppClient = object : ObservedAppClient {
        override fun createOrder(
            scenario: CheckoutScenario,
            customerId: String,
            items: List<CreateOrderItem>,
        ): CreateOrderResult {
            onCreateOrder()
            return CreateOrderResult(ORDER_ID, customerId, "CREATED")
        }

        override fun reserveOrder(scenario: CheckoutScenario, orderId: String) =
            ReserveOrderResult.Success(orderId, "RESERVED")

        override fun payOrder(scenario: CheckoutScenario, orderId: String, paymentMethod: String) =
            PayOrderResult.Success(orderId, "PAID")

        override fun shipOrder(scenario: CheckoutScenario, orderId: String, shippingMethod: String) =
            ShipOrderResult.Success(orderId, STATE_SHIPPED, shippingMethod)

        override fun cancelOrder(scenario: CheckoutScenario, orderId: String, reason: String) =
            CancelOrderResult.Success(orderId, "CANCELLED", reason)

        override fun getOrder(scenario: CheckoutScenario, orderId: String) =
            OrderDetails(orderId, "cust-1", STATE_SHIPPED, 10.0, "USD", 1)
    }

    private companion object {
        const val ORDER_ID = "order-1"
        const val STATE_SHIPPED = "SHIPPED"
        const val SCENARIO_HOLD_DURATION_MS = 10L
        const val TOGGLE_INTERVAL_NANOS = 1_000_000L
        const val STARTUP_CANCEL_HOLD_MS = 50L
    }
}
