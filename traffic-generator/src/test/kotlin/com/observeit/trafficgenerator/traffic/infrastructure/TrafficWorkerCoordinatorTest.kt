package com.observeit.trafficgenerator.traffic.infrastructure

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
import com.observeit.trafficgenerator.traffic.domain.ScenarioExecutionResult
import com.observeit.trafficgenerator.traffic.domain.ScenarioSelector
import com.observeit.trafficgenerator.traffic.domain.ShipOrderResult
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TrafficWorkerCoordinatorTest {

    private var coordinator: TrafficWorkerCoordinator? = null

    private val dummyClient = object : ObservedAppClient {
        override fun createOrder(scenario: CheckoutScenario, customerId: String, items: List<CreateOrderItem>) =
            CreateOrderResult("order-1", customerId, "CREATED")

        override fun reserveOrder(scenario: CheckoutScenario, orderId: String) =
            ReserveOrderResult.Success(orderId, "RESERVED")

        override fun payOrder(scenario: CheckoutScenario, orderId: String, paymentMethod: String) =
            PayOrderResult.Success(orderId, "PAID")

        override fun shipOrder(scenario: CheckoutScenario, orderId: String, shippingMethod: String) =
            ShipOrderResult.Success(orderId, "SHIPPED", shippingMethod)

        override fun cancelOrder(scenario: CheckoutScenario, orderId: String, reason: String) =
            CancelOrderResult.Success(orderId, "CANCELLED", reason)

        override fun getOrder(scenario: CheckoutScenario, orderId: String) =
            OrderDetails(orderId, "cust-1", "SHIPPED", 10.0, "USD", 1)
    }

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

        val runner = object : CheckoutScenarioRunner(dummyClient, NoOpStepPacer) {
            override fun execute(scenario: CheckoutScenario): ScenarioExecutionResult {
                val currentThread = Thread.currentThread()
                workerThreads.add(currentThread.name)
                virtualFlags.add(currentThread.isVirtual)
                executionCount.incrementAndGet()
                return ScenarioExecutionResult(
                    scenario = scenario,
                    orderId = "order-1",
                    customerId = "customer-1",
                    finalState = "SHIPPED",
                    success = true,
                )
            }
        }

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

        val runner = object : CheckoutScenarioRunner(dummyClient, NoOpStepPacer) {
            override fun execute(scenario: CheckoutScenario): ScenarioExecutionResult {
                val count = executionAttempts.incrementAndGet()
                if (count % 2 == 1) {
                    error("Simulated transient failure")
                }
                return ScenarioExecutionResult(
                    scenario = scenario,
                    orderId = "order-$count",
                    customerId = "customer-1",
                    finalState = "SHIPPED",
                    success = true,
                )
            }
        }

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
}
