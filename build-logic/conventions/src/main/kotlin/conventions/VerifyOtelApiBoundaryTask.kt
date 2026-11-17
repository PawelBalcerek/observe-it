package conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verifies resolved module coordinates in memory without producing file outputs")
abstract class VerifyOtelApiBoundaryTask : DefaultTask() {
    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val runtimeModules: ListProperty<String>

    @TaskAction
    fun verify() {
        val modules = runtimeModules.get().distinct()
        if (API_MODULE !in modules) {
            throw GradleException(
                "${projectPath.get()} runtimeClasspath must contain $API_MODULE"
            )
        }

        val disallowed = modules.filter { module ->
            DISALLOWED_PREFIXES.any { prefix -> module.startsWith(prefix) }
        }
        if (disallowed.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} runtimeClasspath contains disallowed OpenTelemetry dependencies: $disallowed"
            )
        }

        val unexpected = modules.filter {
            it.startsWith(OTEL_GROUP_PREFIX) && it !in ALLOWED_OTEL_MODULES
        }
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} runtimeClasspath contains unexpected OpenTelemetry dependencies: $unexpected"
            )
        }
    }

    private companion object {
        const val OTEL_GROUP_PREFIX = "io.opentelemetry:"
        const val API_MODULE = "io.opentelemetry:opentelemetry-api"
        val DISALLOWED_PREFIXES = listOf(
            "io.opentelemetry:opentelemetry-sdk",
            "io.opentelemetry.instrumentation:"
        )
        val ALLOWED_OTEL_MODULES = setOf(
            API_MODULE,
            "io.opentelemetry:opentelemetry-context",
            "io.opentelemetry:opentelemetry-common"
        )
    }
}
