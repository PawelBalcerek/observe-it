package com.observeit.observedapp

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class LoggingFormatTest : IntegrationTest() {

    @BeforeEach
    fun setUp() {
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun `console log pattern formats trace_id span_id and order_id correlation placeholders`() {
        // given
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = rootLogger.getAppender("CONSOLE") as? OutputStreamAppender<*>
        assertNotNull(appender, "CONSOLE appender must be present")

        val encoder = appender?.encoder as? PatternLayoutEncoder
        assertNotNull(encoder, "PatternLayoutEncoder must be configured on CONSOLE appender")

        val pattern = encoder!!.pattern
        assertTrue(
            pattern.contains("trace_id") && pattern.contains("span_id") && pattern.contains("order.id"),
            "Console pattern '$pattern' must contain trace_id, span_id, and order.id placeholders",
        )

        // and
        MDC.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736")
        MDC.put("span_id", "00f067aa0ba902b7")
        MDC.put("order.id", "ord-12345")

        // and
        val event = LoggingEvent(
            LoggingFormatTest::class.java.name,
            rootLogger,
            ch.qos.logback.classic.Level.INFO,
            "Sample order log message",
            null,
            null,
        )

        // when
        val formatted = String(encoder.encode(event))

        // then
        assertTrue(
            formatted.contains("[4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7]"),
            "Formatted log should contain [trace_id,span_id] correlation block, but was: $formatted",
        )

        // and
        assertTrue(
            formatted.contains("[ord-12345]"),
            "Formatted log should contain [order.id] correlation block, but was: $formatted",
        )
    }
}
