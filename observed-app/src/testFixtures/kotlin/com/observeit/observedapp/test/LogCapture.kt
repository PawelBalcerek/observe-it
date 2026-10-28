package com.observeit.observedapp.test

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.observeit.observedapp.correlation.CorrelationContext
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory

class LogCapture(private val loggerName: String = ROOT_LOGGER_NAME) :
    BeforeEachCallback,
    AfterEachCallback {

    private val targetLogger = LoggerFactory.getLogger(loggerName) as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    init {
        start()
    }

    private fun start() {
        if (!listAppender.isStarted) {
            listAppender.start()
            targetLogger.addAppender(listAppender)
        }
    }

    private fun stop() {
        if (listAppender.isStarted) {
            targetLogger.detachAppender(listAppender)
            listAppender.stop()
        }
    }

    private fun clear() {
        listAppender.list.clear()
    }

    fun assertLogged(
        level: Level = INFO,
        message: String? = null,
        keyValues: Map<String, Any?> = emptyMap(),
        mdc: Map<String, String> = emptyMap(),
    ): ILoggingEvent {
        val match = listAppender.list.firstOrNull { event ->
            (event.level == level) &&
                (message == null || event.message == message) &&
                keyValues.all { (k, v) -> event.keyValue(k) == v } &&
                mdc.all { (k, v) -> event.mdcPropertyMap[k] == v }
        }
        match.shouldNotBeNull()
        return match
    }

    override fun beforeEach(context: ExtensionContext) {
        clear()
        start()
        CorrelationContext.clear()
    }

    override fun afterEach(context: ExtensionContext) {
        stop()
        CorrelationContext.clear()
    }
}

private fun ILoggingEvent.keyValue(key: String): Any? = keyValuePairs?.firstOrNull { it.key == key }?.value
