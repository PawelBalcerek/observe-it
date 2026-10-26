package com.observeit.observedapp.test

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.observeit.observedapp.correlation.CorrelationContext
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory

class LogCapture(private val loggerName: String = Logger.ROOT_LOGGER_NAME) :
    BeforeEachCallback,
    AfterEachCallback,
    AutoCloseable {

    private val targetLogger = LoggerFactory.getLogger(loggerName) as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    init {
        start()
    }

    val events: List<ILoggingEvent>
        get() = listAppender.list.toList()

    fun start() {
        if (!listAppender.isStarted) {
            listAppender.start()
            targetLogger.addAppender(listAppender)
        }
    }

    fun stop() {
        if (listAppender.isStarted) {
            targetLogger.detachAppender(listAppender)
            listAppender.stop()
        }
    }

    fun clear() {
        listAppender.list.clear()
    }

    fun singleWithKeyValue(key: String, value: Any? = null): ILoggingEvent {
        val matches = listAppender.list.filter { event ->
            event.keyValuePairs?.any { it.key == key && (value == null || it.value == value) } == true
        }
        matches.size shouldBe 1
        return matches.first()
    }

    fun assertLogged(
        level: Level = Level.INFO,
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

    override fun close() {
        stop()
        CorrelationContext.clear()
    }

    companion object {
        fun captureLogs(loggerName: String = Logger.ROOT_LOGGER_NAME, block: (LogCapture) -> Unit) {
            LogCapture(loggerName).use(block)
        }
    }
}

fun ILoggingEvent.keyValue(key: String): Any? = keyValuePairs?.firstOrNull { it.key == key }?.value

fun ILoggingEvent.assertKeyValues(vararg pairs: Pair<String, Any?>) {
    keyValuePairs.shouldNotBeNull()
    val map = keyValuePairs.associate { it.key to it.value }
    for ((key, expected) in pairs) {
        map[key] shouldBe expected
    }
}
