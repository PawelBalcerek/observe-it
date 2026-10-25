package com.observeit.trafficgenerator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
        assertThrows(ClassNotFoundException::class.java) { loadApi() }

        // and
        assertThrows(ClassNotFoundException::class.java) { loadSdk() }
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
        assertFalse(
            otelEntries.isNotEmpty(),
            "Found unexpected OpenTelemetry dependencies on classpath: $otelEntries",
        )
    }
}
