package com.observeit.e2e

import com.observeit.e2e.fixtures.E2eSuiteHarness
import com.observeit.e2e.fixtures.E2eSuiteHarness.dashboards
import com.observeit.e2e.fixtures.E2eSuiteHarness.openSearch
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ObservabilityWorkspaceE2eTest {
    @Test
    fun `observe-it workspace is provisioned with use-case-observability feature`() {
        // given
        val workspaceId = WORKSPACE_ID

        // when
        val response = dashboards.getWorkspace(workspaceId)
        val result = response.path("result")
        val features = result.path("features").values().map { it.asString("") }

        // then
        assertSoftly {
            response.path("success").asBoolean(false) shouldBe true
            result.path("id").asString("") shouldBe workspaceId
            result.path("name").asString("") shouldBe "Observe-It"
            features shouldContain "use-case-observability"
        }
    }

    @Test
    fun `OpenSearch SQL datasource observe_it_prometheus is registered for Prometheus`() {
        // given
        val datasourceName = PROMETHEUS_DATASOURCE

        // when
        val datasource = openSearch.getSqlDatasource(datasourceName)

        // then
        assertSoftly {
            datasource.path("name").asString("") shouldBe datasourceName
            datasource.path("connector").asString("").lowercase() shouldBe "prometheus"
            datasource.path("properties").path("prometheus.uri").asString("") shouldBe "http://prometheus:9090"
        }
    }

    @Test
    fun `OpenSearch executes live PromQL pass-through query against observe_it_prometheus`() {
        // given
        val metricName = "orders_created_total"

        // when / then
        await atMost AWAIT_TIMEOUT untilAsserted {
            val nowEpochSeconds = Instant.now().epochSecond
            val startEpochSeconds = nowEpochSeconds - 300
            val series = openSearch.queryPromQlRange(
                datasourceName = PROMETHEUS_DATASOURCE,
                promQl = metricName,
                startEpochSeconds = startEpochSeconds,
                endEpochSeconds = nowEpochSeconds,
            )
            val services = series.mapNotNull { it.labels["service_name"] }
            val maxMetricValue = series.flatMap { it.values }.maxOrNull() ?: 0.0

            assertSoftly {
                series.shouldNotBeEmpty()
                services shouldContain "observed-app"
                maxMetricValue shouldBeGreaterThanOrEqual 7.0
            }
        }
    }

    private companion object {
        const val WORKSPACE_ID = "observe-it"
        const val PROMETHEUS_DATASOURCE = "observe_it_prometheus"
        val AWAIT_TIMEOUT: Duration = Duration.ofSeconds(30)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            E2eSuiteHarness.happyPathOrderId()
        }
    }
}
