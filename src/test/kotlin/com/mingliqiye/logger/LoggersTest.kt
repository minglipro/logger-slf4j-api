package com.mingliqiye.logger

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.event.Level
import java.util.concurrent.CopyOnWriteArrayList

class TestAppender(name: String) :
    AbstractAppender(name, null, null, true, Property.EMPTY_ARRAY) {

    val events: MutableList<LogEvent> = CopyOnWriteArrayList()

    override fun append(event: LogEvent) {
        events.add(event.toImmutable())
    }

    fun clear() = events.clear()

    fun lastMessage(): String? = events.lastOrNull()?.message?.formattedMessage

    fun lastLevel(): org.apache.logging.log4j.Level? = events.lastOrNull()?.level

    fun lastThrowable(): Throwable? = events.lastOrNull()?.thrown
}

class LoggersTest {

    private lateinit var appender: TestAppender
    private lateinit var ctx: LoggerContext

    @BeforeEach
    fun setup() {
        ctx = LogManager.getContext(false) as LoggerContext
        val config = ctx.configuration
        appender = TestAppender("LoggersTestAppender").also { it.start() }
        config.addAppender(appender)
        config.rootLogger.level = org.apache.logging.log4j.Level.ALL
        config.rootLogger.addAppender(appender, org.apache.logging.log4j.Level.ALL, null)
        ctx.updateLoggers()
    }

    @AfterEach
    fun teardown() {
        appender.stop()
        ctx.configuration.rootLogger.removeAppender("LoggersTestAppender")
        ctx.updateLoggers()
    }

    private fun testLogger(name: String = "LoggersTest"): Logger = getLogger(name)

    // ==================== getLogger 系列 ====================

    @Nested
    inner class GetLoggerTests {

        @Test
        fun `getLogger by reified generic returns logger with class name`() {
            val logger = getLogger<LoggersTest>()
            assertNotNull(logger)
            assertEquals(LoggersTest::class.java.name, logger.name)
        }

        @Test
        fun `getLogger by Class returns logger with class name`() {
            val logger = getLogger(String::class.java)
            assertNotNull(logger)
            assertEquals("java.lang.String", logger.name)
        }

        @Test
        fun `getLogger by KClass returns logger with class name`() {
            val logger = getLogger(LoggersTest::class)
            assertNotNull(logger)
            assertEquals(LoggersTest::class.java.name, logger.name)
        }

        @Test
        fun `getLogger by name returns logger with that name`() {
            val logger = getLogger("custom.logger.name")
            assertNotNull(logger)
            assertEquals("custom.logger.name", logger.name)
        }

        @Test
        fun `getLogger caches same name to same instance`() {
            val a = getLogger("cache.test")
            val b = getLogger("cache.test")
            assertSame(a, b)
        }

        @Test
        fun `getLogger different names return different instances`() {
            val a = getLogger("name.alpha")
            val b = getLogger("name.beta")
            assertNotSame(a, b)
        }

        @Test
        fun `getLogger by Class caches`() {
            val a = getLogger(LoggersTest::class.java)
            val b = getLogger(LoggersTest::class.java)
            assertSame(a, b)
        }

        @Test
        fun `Class extension getLogger works`() {
            val logger = LoggersTest::class.java.getLogger()
            assertEquals(LoggersTest::class.java.name, logger.name)
        }

        @Test
        fun `KClass extension getLogger works`() {
            val logger = LoggersTest::class.getLogger()
            assertEquals(LoggersTest::class.java.name, logger.name)
        }

        @Test
        fun `Any logger extension uses runtime class`() {
            val obj = "hello"
            val logger = obj.logger()
            assertEquals(String::class.java.name, logger.name)
        }

        @Test
        fun `top-level logger shorthand works`() {
            val logger = logger<LoggersTest>()
            assertEquals(LoggersTest::class.java.name, logger.name)
        }
    }

    // ==================== getTraceLogger ====================

    @Nested
    inner class TraceLoggerTests {

        @Test
        fun `getTraceLogger returns caller class name not Loggers class`() {
            val logger = getTraceLogger()
            assertNotNull(logger)
            // 修复前 bug：返回的是 LoggersKt（getTraceLogger 自身所在类）
            // 修复后：返回调用方类名
            assertTrue(
                logger.name.contains("LoggersTest"),
                "Expected caller class name containing 'LoggersTest', got: ${logger.name}"
            )
        }

        @Test
        fun `getTraceLogger from nested call returns immediate caller`() {
            fun inner(): Logger = getTraceLogger()
            val logger = inner()
            assertNotNull(logger)
            assertTrue(logger.name.isNotBlank())
        }
    }

    // ==================== 常规级别方法 ====================

    @Nested
    inner class LevelMethodTests {

        @Test
        fun `info with string object logs at INFO level`() {
            val logger = testLogger()
            logger.info("hello info")
            assertEquals("hello info", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.INFO, appender.lastLevel())
        }

        @Test
        fun `info with null logs 'null'`() {
            val logger = testLogger()
            logger.info(null as Any?)
            assertEquals("null", appender.lastMessage())
        }

        @Test
        fun `info with throwable logs message and stack`() {
            val logger = testLogger()
            val ex = RuntimeException("boom")
            logger.info("context msg", ex)
            assertEquals("context msg", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `info with lambda defers evaluation`() {
            val logger = testLogger()
            var evaluated = false
            logger.info { evaluated = true; "lazy msg" }
            assertTrue(evaluated)
            assertEquals("lazy msg", appender.lastMessage())
        }

        @Test
        fun `debug with string logs at DEBUG level`() {
            val logger = testLogger()
            logger.debug("hello debug")
            assertEquals("hello debug", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.DEBUG, appender.lastLevel())
        }

        @Test
        fun `trace with string logs at TRACE level`() {
            val logger = testLogger()
            logger.trace("hello trace")
            assertEquals("hello trace", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.TRACE, appender.lastLevel())
        }

        @Test
        fun `warn with string logs at WARN level`() {
            val logger = testLogger()
            logger.warn("hello warn")
            assertEquals("hello warn", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.WARN, appender.lastLevel())
        }

        @Test
        fun `error with string logs at ERROR level`() {
            val logger = testLogger()
            logger.error("hello error")
            assertEquals("hello error", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.ERROR, appender.lastLevel())
        }

        @Test
        fun `error with throwable logs stack`() {
            val logger = testLogger()
            val ex = IllegalStateException("bad state")
            logger.error("err ctx", ex)
            assertEquals("err ctx", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `all levels with arbitrary object call toString`() {
            val logger = testLogger()
            val obj = object {
                override fun toString() = "custom-toString"
            }
            logger.info(obj)
            assertEquals("custom-toString", appender.lastMessage())
        }
    }

    // ==================== Infix 方法 ====================

    @Nested
    inner class InfixTests {

        @Test
        fun `infix info with string`() {
            val logger = testLogger()
            logger info "infix info msg"
            assertEquals("infix info msg", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.INFO, appender.lastLevel())
        }

        @Test
        fun `infix info with null logs 'null'`() {
            val logger = testLogger()
            logger info null
            assertEquals("null", appender.lastMessage())
        }

        @Test
        fun `infix info with lambda`() {
            val logger = testLogger()
            logger info { "infix lazy" }
            assertEquals("infix lazy", appender.lastMessage())
        }

        @Test
        fun `infix info with throwable`() {
            val logger = testLogger()
            val ex = RuntimeException("info ex")
            logger info ex
            assertEquals("info ex", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
            assertEquals(org.apache.logging.log4j.Level.INFO, appender.lastLevel())
        }

        @Test
        fun `infix debug with string`() {
            val logger = testLogger()
            logger debug "infix debug"
            assertEquals("infix debug", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.DEBUG, appender.lastLevel())
        }

        @Test
        fun `infix debug with lambda`() {
            val logger = testLogger()
            logger debug { "debug lazy" }
            assertEquals("debug lazy", appender.lastMessage())
        }

        @Test
        fun `infix debug with throwable`() {
            val logger = testLogger()
            val ex = RuntimeException("debug ex")
            logger debug ex
            assertEquals("debug ex", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `infix warn with string`() {
            val logger = testLogger()
            logger warn "infix warn"
            assertEquals("infix warn", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.WARN, appender.lastLevel())
        }

        @Test
        fun `infix warn with lambda`() {
            val logger = testLogger()
            logger warn { "warn lazy" }
            assertEquals("warn lazy", appender.lastMessage())
        }

        @Test
        fun `infix warn with throwable`() {
            val logger = testLogger()
            val ex = RuntimeException("warn ex")
            logger warn ex
            assertEquals("warn ex", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `infix error with string`() {
            val logger = testLogger()
            logger error "infix error"
            assertEquals("infix error", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.ERROR, appender.lastLevel())
        }

        @Test
        fun `infix error with lambda`() {
            val logger = testLogger()
            logger error { "error lazy" }
            assertEquals("error lazy", appender.lastMessage())
        }

        @Test
        fun `infix error with throwable`() {
            val logger = testLogger()
            val ex = RuntimeException("error ex")
            logger error ex
            assertEquals("error ex", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `infix trace with string`() {
            val logger = testLogger()
            logger trace "infix trace"
            assertEquals("infix trace", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.TRACE, appender.lastLevel())
        }

        @Test
        fun `infix trace with lambda`() {
            val logger = testLogger()
            logger trace { "trace lazy" }
            assertEquals("trace lazy", appender.lastMessage())
        }

        @Test
        fun `infix trace with throwable`() {
            val logger = testLogger()
            val ex = RuntimeException("trace ex")
            logger trace ex
            assertEquals("trace ex", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `infix error with throwable having null message logs 'null'`() {
            val logger = testLogger()
            val ex = RuntimeException(null as String?)
            logger error ex
            assertEquals("null", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }
    }

    // ==================== 通用 log(Level, ...) ====================

    @Nested
    inner class GenericLogTests {

        @Test
        fun `log with Level_INFO and string`() {
            val logger = testLogger()
            logger.log(Level.INFO, "generic info")
            assertEquals("generic info", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.INFO, appender.lastLevel())
        }

        @Test
        fun `log with Level_ERROR and throwable`() {
            val logger = testLogger()
            val ex = RuntimeException("generic err")
            logger.log(Level.ERROR, "ctx", ex)
            assertEquals("ctx", appender.lastMessage())
            assertSame(ex, appender.lastThrowable())
        }

        @Test
        fun `log with Level_DEBUG and lambda`() {
            val logger = testLogger()
            logger.log(Level.DEBUG) { "generic lazy" }
            assertEquals("generic lazy", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.DEBUG, appender.lastLevel())
        }

        @Test
        fun `infix log with Pair`() {
            val logger = testLogger()
            logger log (Level.WARN to "pair warn")
            assertEquals("pair warn", appender.lastMessage())
            assertEquals(org.apache.logging.log4j.Level.WARN, appender.lastLevel())
        }

        @Test
        fun `infix log with Pair null value`() {
            val logger = testLogger()
            logger log (Level.INFO to null)
            assertEquals("null", appender.lastMessage())
        }

        @Test
        fun `log all levels produce correct level`() {
            val logger = testLogger()
            val levels = listOf(
                Level.TRACE to org.apache.logging.log4j.Level.TRACE,
                Level.DEBUG to org.apache.logging.log4j.Level.DEBUG,
                Level.INFO to org.apache.logging.log4j.Level.INFO,
                Level.WARN to org.apache.logging.log4j.Level.WARN,
                Level.ERROR to org.apache.logging.log4j.Level.ERROR
            )
            for ((slf4jLevel, log4jLevel) in levels) {
                appender.clear()
                logger.log(slf4jLevel, "msg-${slf4jLevel.name}")
                assertEquals(log4jLevel, appender.lastLevel(), "Level mismatch for ${slf4jLevel.name}")
                assertEquals("msg-${slf4jLevel.name}", appender.lastMessage())
            }
        }
    }

    // ==================== ifXxxEnabled 守卫 ====================

    @Nested
    inner class GuardTests {

        @Test
        fun `ifInfoEnabled executes block when INFO enabled`() {
            val logger = testLogger()
            var executed = false
            logger.ifInfoEnabled { executed = true }
            assertTrue(executed, "INFO should be enabled at ALL level")
        }

        @Test
        fun `ifDebugEnabled executes block when DEBUG enabled`() {
            val logger = testLogger()
            var executed = false
            logger.ifDebugEnabled { executed = true }
            assertTrue(executed)
        }

        @Test
        fun `ifTraceEnabled executes block when TRACE enabled`() {
            val logger = testLogger()
            var executed = false
            logger.ifTraceEnabled { executed = true }
            assertTrue(executed)
        }

        @Test
        fun `ifWarnEnabled executes block`() {
            val logger = testLogger()
            var executed = false
            logger.ifWarnEnabled { executed = true }
            assertTrue(executed)
        }

        @Test
        fun `ifErrorEnabled executes block`() {
            val logger = testLogger()
            var executed = false
            logger.ifErrorEnabled { executed = true }
            assertTrue(executed)
        }

        @Test
        fun `ifEnabled with Level_INFO executes block`() {
            val logger = testLogger()
            var executed = false
            logger.ifEnabled(Level.INFO) { executed = true }
            assertTrue(executed)
        }

        @Test
        fun `ifEnabled with Level_TRACE executes block`() {
            val logger = testLogger()
            var executed = false
            logger.ifEnabled(Level.TRACE) { executed = true }
            assertTrue(executed)
        }
    }

    // ==================== 边界与回归 ====================

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `logging empty string works`() {
            val logger = testLogger()
            logger.info("")
            assertEquals("", appender.lastMessage())
        }

        @Test
        fun `logging object whose toString throws is handled`() {
            val logger = testLogger()
            val obj = object {
                override fun toString(): String = throw AssertionError("should not be called directly")
            }
            // logAt 内部使用 any?.toString()，会触发异常；
            // 此用例验证异常会向上传播（SLF4J 不吞异常）
            org.junit.jupiter.api.assertThrows<AssertionError> {
                logger.info(obj as Any?)
            }
        }

        @Test
        fun `multiple sequential logs all captured`() {
            val logger = testLogger()
            appender.clear()
            logger.info("first")
            logger.warn("second")
            logger.error("third")
            assertEquals(3, appender.events.size)
            assertEquals("first", appender.events[0].message.formattedMessage)
            assertEquals("second", appender.events[1].message.formattedMessage)
            assertEquals("third", appender.events[2].message.formattedMessage)
        }

        @Test
        fun `getTraceLogger does not return Loggers internal class`() {
            val logger = getTraceLogger()
            // 回归测试：修复前 bug 返回的是 getTraceLogger 自身所在的文件类
            // （@file:JvmName("Loggers") → com.mingliqiye.logger.Loggers）
            assertNotEquals(
                "com.mingliqiye.logger.Loggers",
                logger.name,
                "getTraceLogger must not return its own file class"
            )
            assertTrue(
                logger.name.contains("LoggersTest"),
                "Expected caller class containing 'LoggersTest', got: ${logger.name}"
            )
        }
    }
}
