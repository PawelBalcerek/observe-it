package com.observeit.observedapp.order.domain

import com.observeit.observedapp.order.OrderFixtures.anOrder
import com.observeit.observedapp.order.domain.OrderState.CANCELLED
import com.observeit.observedapp.order.domain.OrderState.CREATED
import com.observeit.observedapp.order.domain.OrderState.FAILED
import com.observeit.observedapp.order.domain.OrderState.PAID
import com.observeit.observedapp.order.domain.OrderState.RESERVED
import com.observeit.observedapp.order.domain.OrderState.SHIPPED
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OrderTest {

    @Test
    fun `reserves order when state is CREATED`() {
        // given
        val order = anOrder()

        // when
        val reserved = order.reserve()

        // then
        reserved shouldBe anOrder(state = RESERVED)
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when reserving order in non-CREATED state`() {
        // given
        val order = anOrder(state = RESERVED)

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            order.reserve()
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = order.id,
            state = RESERVED,
            attemptedAction = "reserve",
        )
    }

    @Test
    fun `pays order when state is RESERVED`() {
        // given
        val order = anOrder(state = RESERVED)

        // when
        val paid = order.pay()

        // then
        paid shouldBe anOrder(state = PAID)
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when paying order in non-RESERVED state`() {
        // given
        val order = anOrder()

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            order.pay()
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = order.id,
            state = CREATED,
            attemptedAction = "pay",
        )
    }

    @Test
    fun `ships order when state is PAID`() {
        // given
        val order = anOrder(state = PAID)

        // when
        val shipped = order.ship()

        // then
        shipped shouldBe anOrder(state = SHIPPED)
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when shipping order in non-PAID state`() {
        // given
        val order = anOrder()

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            order.ship()
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = order.id,
            state = CREATED,
            attemptedAction = "ship",
        )
    }

    @Test
    fun `cancels order when state is CREATED`() {
        // given
        val order = anOrder()

        // when
        val cancelled = order.cancel()

        // then
        cancelled shouldBe anOrder(state = CANCELLED)
    }

    @Test
    fun `cancels order when state is RESERVED`() {
        // given
        val order = anOrder(state = RESERVED)

        // when
        val cancelled = order.cancel()

        // then
        cancelled shouldBe anOrder(state = CANCELLED)
    }

    @Test
    fun `throws InvalidOrderStateTransitionException when cancelling order in non-cancellable state`() {
        // given
        val order = anOrder(state = SHIPPED)

        // when
        val exception = shouldThrow<InvalidOrderStateTransitionException> {
            order.cancel()
        }

        // then
        exception shouldBe InvalidOrderStateTransitionException(
            orderId = order.id,
            state = SHIPPED,
            attemptedAction = "cancel",
        )
    }

    @Test
    fun `fails order transitions state to FAILED`() {
        // given
        val order = anOrder()

        // when
        val failed = order.fail()

        // then
        failed shouldBe anOrder(state = FAILED)
    }
}
