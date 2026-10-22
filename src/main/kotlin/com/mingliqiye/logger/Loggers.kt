@file:JvmName("Loggers")

package com.mingliqiye.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/** 用于 `LocationAwareLogger` 定位真实调用方的 FQCN。 */
private const val FQCN = "com.mingliqiye.logger.Loggers"

/** Logger 缓存，避免对同名 Logger 重复调用 `LoggerFactory.getLogger`。 */
private val loggers: ConcurrentHashMap<String, Logger> = ConcurrentHashMap(128, 0.75f)

/** 缓存查找/创建，使用 `computeIfAbsent` 保证原子性。 */
private fun cachedLogger(name: String, factory: () -> Logger): Logger =
    loggers[name] ?: loggers.computeIfAbsent(name) { factory() }

/** 获取以泛型类型 `T` 的全限定名为名称的 Logger。 */
inline fun <reified T> getLogger(): Logger = getLogger(T::class.java)

/** 获取以 `clazz.name` 为名称的 Logger。 */
fun <T> getLogger(clazz: Class<T>): Logger =
    cachedLogger(clazz.name) { LoggerFactory.getLogger(clazz) }

/** 获取以 `KClass.java.name` 为名称的 Logger。 */
fun getLogger(clazz: KClass<*>): Logger = getLogger(clazz.java)

/** 获取以指定名称命名的 Logger。 */
fun getLogger(name: String): Logger =
    cachedLogger(name) { LoggerFactory.getLogger(name) }

/**
 * 通过抛出 `Throwable` 抓取调用栈，以调用方的类名获取 Logger。
 *
 * 栈帧构造开销较大，热路径上请改用 `getLogger<T>()` 或 `getLogger(Class)`。
 */
fun getTraceLogger(): Logger {
    val stack = Throwable().stackTrace
    // stack[0] = getTraceLogger 自身，stack[1] = 真正的调用方
    val callerClass = stack.getOrNull(1)?.className
    return if (callerClass != null) getLogger(callerClass) else getLogger("Unknown")
}

/** `Class` 上的 `getLogger` 扩展。 */
@JvmName("__Class_getLogger")
fun Class<out Any>.getLogger(): Logger = getLogger(this)

/** `KClass` 上的 `getLogger` 扩展。 */
@JvmName("__KClass_getLogger")
fun KClass<out Any>.getLogger(): Logger = this.java.getLogger()

/** 任意对象上的 `logger()` 扩展，以运行时类的全限定名获取 Logger。 */
fun Any.logger(): Logger = getLogger(this.javaClass)

/** 顶层快捷函数：`logger<MyClass>()` 等价于 `getLogger<MyClass>()`。 */
inline fun <reified T> logger(): Logger = getLogger<T>()

/** 将 [Level] 转换为 SLF4J 的 int 级别码。 */
private fun Level.toInt(): Int = when (this) {
    Level.TRACE -> LocationAwareLogger.TRACE_INT
    Level.DEBUG -> LocationAwareLogger.DEBUG_INT
    Level.INFO -> LocationAwareLogger.INFO_INT
    Level.WARN -> LocationAwareLogger.WARN_INT
    Level.ERROR -> LocationAwareLogger.ERROR_INT
}

/** 走 `LocationAwareLogger` 路径，保留调用方源码位置。 */
private fun Level.logAt(locationAwareLogger: LocationAwareLogger, msg: String, throwable: Throwable? = null) {
    locationAwareLogger.log(null, FQCN, this.toInt(), msg, null, throwable)
}

/** 消息以 `() -> String` 形式延迟求值；非 `LocationAwareLogger` 时回退到 `LoggingEventBuilder`。 */
private fun Level.logAt(logger: Logger, msg: () -> String, throwable: Throwable? = null) {
    if (logger is LocationAwareLogger) {
        logAt(logger, msg(), throwable)
    } else {
        logger.atLevel(this).setCause(throwable).log(msg)
    }
}

/** 消息以 `() -> Any?` 形式延迟求值，null 输出为 `"null"`。 */
@JvmName("logAt_Any")
private fun Level.logAt(logger: Logger, caller: () -> Any?, throwable: Throwable? = null) {
    logAt(logger, { caller()?.toString() ?: "null" }, throwable)
}

/** 消息以任意对象直接传入，null 输出为 `"null"`。 */
private fun Level.logAt(logger: Logger, any: Any?, throwable: Throwable? = null) {
    logAt(logger, { any?.toString() ?: "null" }, throwable)
}

/** 通用级别日志：直接传入消息字符串。 */
fun Logger.log(level: Level, msg: String, throwable: Throwable? = null) {
    level.logAt(this, msg, throwable)
}

/** 通用级别日志：消息以 lambda 形式延迟求值。 */
fun Logger.log(level: Level, caller: () -> String) {
    level.logAt(this, caller)
}

/** 通用级别 infix 日志：`logger log Level.INFO to "msg"`。 */
infix fun Logger.log(pair: Pair<Level, Any?>) {
    pair.first.logAt(this, pair.second?.toString() ?: "null")
}

/** TRACE：直接传入任意对象，null 输出为 `"null"`。 */
fun Logger.trace(obj: Any?, throwable: Throwable? = null) {
    Level.TRACE.logAt(this, obj, throwable)
}

/** TRACE：消息以 lambda 形式延迟求值，仅在级别启用时才会被调用。 */
fun Logger.trace(throwable: Throwable? = null, caller: () -> String) {
    Level.TRACE.logAt(this, caller, throwable)
}

/** DEBUG：直接传入任意对象，null 输出为 `"null"`。 */
fun Logger.debug(obj: Any?, throwable: Throwable? = null) {
    Level.DEBUG.logAt(this, obj, throwable)
}

/** DEBUG：消息以 lambda 形式延迟求值。 */
fun Logger.debug(throwable: Throwable? = null, caller: () -> String) {
    Level.DEBUG.logAt(this, caller, throwable)
}

/** INFO：直接传入任意对象，null 输出为 `"null"`。 */
fun Logger.info(obj: Any?, throwable: Throwable? = null) {
    Level.INFO.logAt(this, obj, throwable)
}

/** INFO：消息以 lambda 形式延迟求值。 */
fun Logger.info(throwable: Throwable? = null, caller: () -> String) {
    Level.INFO.logAt(this, caller, throwable)
}

/** WARN：直接传入任意对象，null 输出为 `"null"`。 */
fun Logger.warn(obj: Any?, throwable: Throwable? = null) {
    Level.WARN.logAt(this, obj, throwable)
}

/** WARN：消息以 lambda 形式延迟求值。 */
fun Logger.warn(throwable: Throwable? = null, caller: () -> String) {
    Level.WARN.logAt(this, caller, throwable)
}

/** ERROR：直接传入任意对象，null 输出为 `"null"`。 */
fun Logger.error(obj: Any?, throwable: Throwable? = null) {
    Level.ERROR.logAt(this, obj, throwable)
}

/** ERROR：消息以 lambda 形式延迟求值。 */
fun Logger.error(throwable: Throwable? = null, caller: () -> String) {
    Level.ERROR.logAt(this, caller, throwable)
}

/** TRACE infix：任意对象消息，null 输出为 `"null"`。 */
infix fun Logger.trace(obj: Any?) {
    Level.TRACE.logAt(this, obj?.toString() ?: "null")
}

/** TRACE infix：延迟求值的字符串消息。 */
infix fun Logger.trace(caller: () -> String) {
    Level.TRACE.logAt(this, caller)
}

/** TRACE infix：异常消息，自动取 `throwable.message` 作为消息体并附带堆栈。 */
infix fun Logger.trace(throwable: Throwable) {
    Level.TRACE.logAt(this, throwable.message ?: "null", throwable)
}

/** DEBUG infix：任意对象消息，null 输出为 `"null"`。 */
infix fun Logger.debug(obj: Any?) {
    Level.DEBUG.logAt(this, obj?.toString() ?: "null")
}

/** DEBUG infix：延迟求值的字符串消息。 */
infix fun Logger.debug(caller: () -> String) {
    Level.DEBUG.logAt(this, caller)
}

/** DEBUG infix：异常消息，自动取 `throwable.message` 作为消息体并附带堆栈。 */
infix fun Logger.debug(throwable: Throwable) {
    Level.DEBUG.logAt(this, throwable.message ?: "null", throwable)
}

/** INFO infix：任意对象消息，null 输出为 `"null"`。 */
infix fun Logger.info(obj: Any?) {
    Level.INFO.logAt(this, obj?.toString() ?: "null")
}

/** INFO infix：延迟求值的字符串消息。 */
infix fun Logger.info(caller: () -> String) {
    Level.INFO.logAt(this, caller)
}

/** INFO infix：异常消息，自动取 `throwable.message` 作为消息体并附带堆栈。 */
infix fun Logger.info(throwable: Throwable) {
    Level.INFO.logAt(this, throwable.message ?: "null", throwable)
}

/** WARN infix：任意对象消息，null 输出为 `"null"`。 */
infix fun Logger.warn(obj: Any?) {
    Level.WARN.logAt(this, obj?.toString() ?: "null")
}

/** WARN infix：延迟求值的字符串消息。 */
infix fun Logger.warn(caller: () -> String) {
    Level.WARN.logAt(this, caller)
}

/** WARN infix：异常消息，自动取 `throwable.message` 作为消息体并附带堆栈。 */
infix fun Logger.warn(throwable: Throwable) {
    Level.WARN.logAt(this, throwable.message ?: "null", throwable)
}

/** ERROR infix：任意对象消息，null 输出为 `"null"`。 */
infix fun Logger.error(obj: Any?) {
    Level.ERROR.logAt(this, obj?.toString() ?: "null", null)
}

/** ERROR infix：延迟求值的字符串消息。 */
infix fun Logger.error(caller: () -> String) {
    Level.ERROR.logAt(this, caller)
}

/** ERROR infix：异常消息，自动取 `throwable.message` 作为消息体并附带堆栈。 */
infix fun Logger.error(throwable: Throwable) {
    Level.ERROR.logAt(this, throwable.message ?: "null", throwable)
}

/** 当 TRACE 级别启用时执行 [block]，避免不必要的对象构造。 */
inline fun Logger.ifTraceEnabled(block: () -> Unit) {
    if (isTraceEnabled) block()
}

/** 当 DEBUG 级别启用时执行 [block]。 */
inline fun Logger.ifDebugEnabled(block: () -> Unit) {
    if (isDebugEnabled) block()
}

/** 当 INFO 级别启用时执行 [block]。 */
inline fun Logger.ifInfoEnabled(block: () -> Unit) {
    if (isInfoEnabled) block()
}

/** 当 WARN 级别启用时执行 [block]。 */
inline fun Logger.ifWarnEnabled(block: () -> Unit) {
    if (isWarnEnabled) block()
}

/** 当 ERROR 级别启用时执行 [block]。 */
inline fun Logger.ifErrorEnabled(block: () -> Unit) {
    if (isErrorEnabled) block()
}

/** 当指定级别启用时执行 [block]。 */
inline fun Logger.ifEnabled(level: Level, block: () -> Unit) {
    if (isEnabledForLevel(level)) block()
}
