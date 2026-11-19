package com.observeit.observedapp.order.infrastructure

import com.observeit.observedapp.IntegrationTest
import com.observeit.observedapp.order.OrderFixtures.anOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MongoOrderRepositoryIntegrationTest
@Autowired
constructor(private val repository: MongoOrderRepository) :
    IntegrationTest() {

    @Test
    fun `persists and retrieves order by id`() {
        // given
        val order = anOrder()

        // when
        val saved = repository.save(order)
        val found = repository.findById("id")

        // then
        saved shouldBe order
        found shouldBe order
    }

    @Test
    fun `returns null when finding non-existent order by id`() {
        // when
        val found = repository.findById("missing-id")

        // then
        found.shouldBeNull()
    }

    @Test
    fun `counts stored orders via estimatedCount`() {
        // given
        repository.save(anOrder(id = "order-1"))
        repository.save(anOrder(id = "order-2"))

        // when
        val count = repository.count()

        // then
        count shouldBe 2
    }
}
