package com.observeit.trafficgenerator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

class ClasspathVerificationTest {

    @Test
    fun `no OpenTelemetry classes are loadable on classpath`() {
        // given
        val apiClassName = "io.opentelemetry.api.OpenTelemetry"
        val sdkClassName = "io.opentelemetry.sdk.OpenTelemetrySdk"

        // when
        val loadApi = { Class.forName(apiClassName) }
        val loadSdk = { Class.forName(sdkClassName) }

        // then
        shouldThrow<ClassNotFoundException> { loadApi() }

        // and
        shouldThrow<ClassNotFoundException> { loadSdk() }
    }

    @Test
    fun `no OpenTelemetry jar or module exists on classpath`() {
        // given
        val classpath = System.getProperty("java.class.path")

        // when
        val otelEntries = classpath
            .split(File.pathSeparator)
            .filter { it.contains("opentelemetry", ignoreCase = true) }

        // then
        otelEntries.shouldBeEmpty()
    }
}
