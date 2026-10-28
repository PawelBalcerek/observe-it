package com.observeit.trafficgenerator

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger.ROOT_LOGGER_NAME
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
        val rootLogger = LoggerFactory.getLogger(ROOT_LOGGER_NAME) as Logger
        val appender = rootLogger.getAppender("CONSOLE").shouldBeInstanceOf<OutputStreamAppender<*>>()
        val encoder = appender.encoder.shouldBeInstanceOf<PatternLayoutEncoder>()

        assertSoftly(encoder.pattern) {
            shouldContain("trace_id")
            shouldContain("span_id")
            shouldContain("order.id")
        }

        // and
        MDC.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736")
        MDC.put("span_id", "00f067aa0ba902b7")
        MDC.put("order.id", "ord-12345")

        // and
        val event = LoggingEvent(
            LoggingFormatTest::class.java.name,
            rootLogger,
            INFO,
            "Sample traffic log message",
            null,
            null,
        )

        // when
        val formatted = String(encoder.encode(event))

        // then
        formatted shouldContain "[4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7]"

        // and
        formatted shouldContain "[ord-12345]"
    }
}
