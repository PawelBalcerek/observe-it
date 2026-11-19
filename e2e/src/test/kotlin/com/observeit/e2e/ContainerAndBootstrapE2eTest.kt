package com.observeit.e2e

import com.observeit.e2e.config.E2eConfig
import com.observeit.e2e.fixtures.E2eSuiteHarness.actuator
import com.observeit.e2e.fixtures.E2eSuiteHarness.dashboards
import com.observeit.e2e.fixtures.E2eSuiteHarness.docker
import com.observeit.e2e.fixtures.E2eSuiteHarness.ensureStackReady
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContainerAndBootstrapE2eTest {
    @Test
    fun `local and GHCR container images exist for observed-app and traffic-generator`() {
        // given
        val expectedImages = listOf(
            "observe-it/observed-app:${E2eConfig.imageTag}",
            "${E2eConfig.imagePrefix}/observed-app:${E2eConfig.imageTag}",
            "observe-it/traffic-generator:${E2eConfig.imageTag}",
            "${E2eConfig.imagePrefix}/traffic-generator:${E2eConfig.imageTag}",
        )

        // when
        val presenceByImage = expectedImages.associateWith { docker.imageExists(it) }

        // then
        assertSoftly {
            presenceByImage.forEach { (image, exists) ->
                exists shouldBe true
                image.isNotBlank() shouldBe true
            }
        }
    }

    @Test
    fun `docker compose containers, bootstrap job, and service actuator endpoints are healthy`() {
        // given
        ensureStackReady()

        // when
        val opensearchHealth = docker.containerHealthStatus("opensearch")
        val mongodbHealth = docker.containerHealthStatus("mongodb")
        val dashboardsState = dashboards.overallStatusState()
        val collectorState = docker.containerStateStatus("otel-collector")
        val observedAppHealth = actuator.healthStatus(E2eConfig.OBSERVED_APP_BASE_URL)
        val trafficGeneratorHealth = actuator.healthStatus(E2eConfig.TRAFFIC_GENERATOR_BASE_URL)
        val bootstrapState = docker.containerStateStatus("opensearch-bootstrap")
        val bootstrapExitCode = docker.containerExitCode("opensearch-bootstrap")

        // then
        assertSoftly {
            opensearchHealth shouldBe "healthy"
            mongodbHealth shouldBe "healthy"
            dashboardsState shouldBe "green"
            collectorState shouldBe "running"
            observedAppHealth shouldBe "UP"
            trafficGeneratorHealth shouldBe "UP"
            bootstrapState shouldBe "exited"
            bootstrapExitCode shouldBe 0
        }
    }
}
