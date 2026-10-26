package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.order.OrderFixtures.anOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryOrderRepositoryTest {

    @Test
    fun `save stores and retrieves order by id`() {
        // given
        val repository = InMemoryOrderRepository(capacity = 100)
        val order = anOrder(id = "order-1")

        // when
        val saved = repository.save(order)
        val found = repository.findById("order-1")

        // then
        saved shouldBe order

        // and
        found.shouldNotBeNull()
        found shouldBe order
    }

    @Test
    fun `evicts oldest order in FIFO order when capacity is exceeded`() {
        // given
        val repository = InMemoryOrderRepository(capacity = 3)
        val order1 = anOrder(id = "order-1")
        val order2 = anOrder(id = "order-2")
        val order3 = anOrder(id = "order-3")
        val order4 = anOrder(id = "order-4")

        repository.save(order1)
        repository.save(order2)
        repository.save(order3)

        // when
        repository.save(order4)

        // then
        repository.count() shouldBe 3

        // and
        repository.findById("order-1").shouldBeNull()
        repository.findById("order-2").shouldNotBeNull()
        repository.findById("order-3").shouldNotBeNull()
        repository.findById("order-4").shouldNotBeNull()
    }

    @Test
    fun `handles concurrent writes safely without exceeding capacity`() {
        // given
        val capacity = 50
        val repository = InMemoryOrderRepository(capacity = capacity)
        val threadCount = 8
        val ordersPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)

        // when
        for (t in 0 until threadCount) {
            executor.submit {
                for (i in 0 until ordersPerThread) {
                    val orderId = "thread-$t-order-$i"
                    repository.save(anOrder(id = orderId, customerId = "cust-$t"))
                }
            }
        }
        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)

        // then
        finished shouldBe true

        // and
        repository.count() shouldBe capacity
    }
}
