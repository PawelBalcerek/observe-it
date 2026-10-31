package com.observeit.trafficgenerator.traffic

import ch.qos.logback.classic.Level.INFO
import com.observeit.trafficgenerator.IntegrationTest
import com.observeit.trafficgenerator.test.LogCapture
import com.observeit.trafficgenerator.traffic.TrafficFixtures.aTrafficStatusResponse
import com.observeit.trafficgenerator.traffic.api.TrafficApiFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class TrafficPauseResumeIntegrationTest : IntegrationTest() {

    @JvmField
    @RegisterExtension
    final val logCapture = LogCapture()

    private val trafficApi by lazy { TrafficApiFixture(restTestClient) }

    @AfterEach
    fun tearDown() {
        trafficApi.pauseAndExpect200()
    }

    @Test
    fun `post resume transitions generator from paused to running, reflects in status, and emits resume log`() {
        // given
        val initialStatus = trafficApi.getStatusAndExpect200()
        initialStatus shouldBe aTrafficStatusResponse(running = false, paused = true)

        // when
        val resumeResponse = trafficApi.resumeAndExpect200()

        // then
        resumeResponse shouldBe aTrafficStatusResponse(running = true, paused = false)

        // and
        val statusResponse = trafficApi.getStatusAndExpect200()
        statusResponse shouldBe aTrafficStatusResponse(running = true, paused = false)

        // and
        logCapture.assertLogged(
            level = INFO,
            message = "Traffic generator resumed",
            keyValues = mapOf(
                "traffic.running" to true,
                "traffic.paused" to false,
            ),
        )
    }

    @Test
    fun `post pause transitions generator from running to paused, reflects in status, and emits pause log`() {
        // given
        trafficApi.resumeAndExpect200()
        val runningStatus = trafficApi.getStatusAndExpect200()
        runningStatus shouldBe aTrafficStatusResponse(running = true, paused = false)

        // when
        val pauseResponse = trafficApi.pauseAndExpect200()

        // then
        pauseResponse shouldBe aTrafficStatusResponse(running = false, paused = true)

        // and
        val statusResponse = trafficApi.getStatusAndExpect200()
        statusResponse shouldBe aTrafficStatusResponse(running = false, paused = true)

        // and
        logCapture.assertLogged(
            level = INFO,
            message = "Traffic generator paused",
            keyValues = mapOf(
                "traffic.running" to false,
                "traffic.paused" to true,
            ),
        )
    }

    @Test
    fun `post pause is idempotent when traffic generator is already paused`() {
        // given
        val initialStatus = trafficApi.getStatusAndExpect200()
        initialStatus.paused shouldBe true

        // when
        val firstPause = trafficApi.pauseAndExpect200()
        val secondPause = trafficApi.pauseAndExpect200()

        // then
        firstPause shouldBe aTrafficStatusResponse(running = false, paused = true)
        secondPause shouldBe aTrafficStatusResponse(running = false, paused = true)

        // and
        val currentStatus = trafficApi.getStatusAndExpect200()
        currentStatus shouldBe aTrafficStatusResponse(running = false, paused = true)
    }

    @Test
    fun `post resume is idempotent when traffic generator is already running`() {
        // given
        trafficApi.resumeAndExpect200()

        // when
        val firstResume = trafficApi.resumeAndExpect200()
        val secondResume = trafficApi.resumeAndExpect200()

        // then
        firstResume shouldBe aTrafficStatusResponse(running = true, paused = false)
        secondResume shouldBe aTrafficStatusResponse(running = true, paused = false)

        // and
        val currentStatus = trafficApi.getStatusAndExpect200()
        currentStatus shouldBe aTrafficStatusResponse(running = true, paused = false)
    }
}
