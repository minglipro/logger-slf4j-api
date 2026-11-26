package com.mingliqiye.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger

/**
 * SLF4J [Logger] 的包装实现，在完整保留标准日志 API 的同时提供三类增强能力：
 *
 * 1. **位置感知（Location Aware）**：当底层实现支持 [LocationAwareLogger] 时，
 *    日志中的类名、方法名和行号会穿过本包装类，定位到真正的业务调用点，
 *    而不是停留在 `MLogger` 内部。
 * 2. **Kotlin 便捷 API**：支持任意对象作为消息、`() -> String` 惰性求值，
 *    以及可选 [Throwable] / [Marker] 的组合重载。
 * 3. **内置 infix 写法**：`logger info "xxx"`、`logger error exception`，
 *    无需额外 import `MLogger.Companion`。
 *
 * ### 获取实例
 * 构造函数是 `internal` 的，应通过 [Loggers] 获取。同一名称在进程内只创建
 * 一个 [MLogger] 实例：
 * ```kotlin
 * val log = Loggers.getLogger(MyService::class)
 * ```
 *
 * ### 典型用法
 * ```kotlin
 * log.info("user {} logged in", userId)
 * log.debug { "expensive: " + buildDetail() }   // 级别关闭时不会执行
 * log info "启动完成"
 * log error exception
 * log.warn(marker, "带 Marker 的日志")
 * ```
 *
 * ### 位置感知的前提
 * 位置感知依赖底层 [Logger] 同时是 [LocationAwareLogger]。若底层实现不支持
 * （例如 NOP Logger），会退化为 SLF4J 2.x 的 [Logger.atLevel] 构建器，
 * 此时位置信息会停留在本包装类上，这是 SLF4J 的固有限制。
 *
 * ### 线程安全
 * [MLogger] 实例本身没有可变状态，创建后可安全地在多线程间共享；实际的
 * 日志过滤与输出由底层 SLF4J [Logger] 负责。
 *
 * @see Loggers
 * @see LocationAwareLogger
 */
class MLogger : Logger {
	
	/**
	 * 整个类共享的常量。
	 */
	companion object {
		/**
		 * 传给 [LocationAwareLogger.log] 的调用边界 FQCN（本类的完全限定名）。
		 *
		 * 底层实现会跳过类名等于该值的栈帧，从而把日志位置定位到业务调用者。
		 */
		private val FQCN: String = MLogger::class.java.name
	}
	
	/**
	 * 底层 SLF4J [Logger]；所有日志调用最终委托给它。
	 */
	private val logger: Logger
	
	/**
	 * 底层 [Logger] 的位置感知视图；底层实现不是 [LocationAwareLogger] 时为 `null`。
	 */
	private val locationAwareLogger: LocationAwareLogger?
	
	/**
	 * 位置感知能力快照，构造时根据底层 Logger 是否实现 [LocationAwareLogger] 确定。
	 */
	private var isLocationAwareLogger: Boolean = false
	
	/**
	 * 核心日志入口（占位符 + 可变参数形式），供本类标准方法与便捷方法委托。
	 *
	 * 底层支持 [LocationAwareLogger] 时，通过 `FQCN` 跳过本类栈帧，把位置定位到
	 * 业务调用方；否则退回 SLF4J 2.x 的 [Logger.atLevel] 构建器。
	 *
	 * [msg] 中的 `{}` 会按 SLF4J 规则被 [arguments] 依次替换。外部调用方通常
	 * 不需要直接调用本方法，使用标准 `trace` / `debug` / `info` / `warn` / `error`
	 * 或 [MLogger] 提供的便捷重载即可。
	 *
	 * @param level 日志级别，默认 [Level.DEBUG]。
	 * @param msg 日志消息，可包含 `{}` 占位符。
	 * @param throwable 关联异常，会随日志输出完整堆栈。
	 * @param marker 日志标记，用于过滤与路由。
	 * @param arguments 用于替换 `{}` 的参数。
	 */
	fun log(
		level: Level = Level.DEBUG,
		msg: String? = null,
		throwable: Throwable? = null,
		marker: Marker? = null,
		vararg arguments: Any? = emptyArray()
	) {
		if (isLocationAwareLogger) {
			locationAwareLogger!!.log(
				marker, FQCN, level.toInt(), msg, arguments, throwable
			)
		} else {
			logger.atLevel(level).addMarker(marker).setCause(throwable).log(
				msg, *arguments
			)
		}
	}
	
	/**
	 * 按名称创建 [MLogger]。
	 *
	 * 仅供 [Loggers] 内部使用；业务代码应通过 [Loggers.getLogger] 获取带缓存的
	 * 实例。
	 *
	 * @param name Logger 名称，通常是类的完全限定名。
	 */
	internal constructor(name: String) {
		logger = LoggerFactory.getLogger(name)
		locationAwareLogger = (logger as? LocationAwareLogger)?.apply {
			isLocationAwareLogger = true
		}
	}
	
	/**
	 * 按 [Class] 创建 [MLogger]，Logger 名称为 [Class.getName]。
	 *
	 * 仅供 [Loggers] 内部使用；业务代码应通过 [Loggers.getLogger] 获取带缓存的
	 * 实例。
	 *
	 * @param clazz 用于推断名称的类。
	 */
	internal constructor(clazz: Class<*>) {
		logger = LoggerFactory.getLogger(clazz)
		locationAwareLogger = (logger as? LocationAwareLogger)?.apply {
			isLocationAwareLogger = true
		}
	}
	
	/**
	 * 以中缀写法记录一条 DEBUG 日志。
	 *
	 * [string] 作为 `{}` 占位符参数传给底层日志，等价于：
	 * ```kotlin
	 * debug("{}", string)
	 * ```
	 * 底层会调用 [string] 的 `toString()` 填充占位符；传 `null` 时显示 `null`。
	 *
	 * ```kotlin
	 * logger debug "cache miss"
	 * ```
	 *
	 * @param string 消息对象；允许为 `null`。
	 */
	infix fun debug(string: Any?) {
		debug("{}", string)
	}

	/**
	 * 以中缀写法记录一条带异常堆栈的 DEBUG 日志。
	 *
	 * 等价于 `debug("{}", t?.message, t)`：异常消息作为日志文本，[t] 本身作为
	 * 异常随日志输出。`t` 为 `null` 时没有堆栈，日志文本为 `null`。
	 *
	 * ```kotlin
	 * logger debug exception
	 * ```
	 *
	 * @param t 要记录的异常；允许为 `null`。
	 */
	infix fun debug(t: Throwable?) {
		debug("{}", t?.message, t)
	}

	/**
	 * 以中缀写法记录一条 INFO 日志。
	 *
	 * [string] 作为 `{}` 占位符参数传给底层日志，等价于：
	 * ```kotlin
	 * info("{}", string)
	 * ```
	 * 底层会调用 [string] 的 `toString()` 填充占位符；传 `null` 时显示 `null`。
	 *
	 * ```kotlin
	 * logger info "订单创建完成"
	 * ```
	 *
	 * @param string 消息对象；允许为 `null`。
	 */
	infix fun info(string: Any?) {
		info("{}", string)
	}

	/**
	 * 以中缀写法记录一条带异常堆栈的 INFO 日志。
	 *
	 * 等价于 `info("{}", t?.message, t)`：异常消息作为日志文本，[t] 本身作为
	 * 异常随日志输出。`t` 为 `null` 时没有堆栈，日志文本为 `null`。
	 *
	 * ```kotlin
	 * logger info exception
	 * ```
	 *
	 * @param t 要记录的异常；允许为 `null`。
	 */
	infix fun info(t: Throwable?) {
		info("{}", t?.message, t)
	}

	/**
	 * 以中缀写法记录一条 TRACE 日志。
	 *
	 * [string] 作为 `{}` 占位符参数传给底层日志，等价于：
	 * ```kotlin
	 * trace("{}", string)
	 * ```
	 * 底层会调用 [string] 的 `toString()` 填充占位符；传 `null` 时显示 `null`。
	 *
	 * ```kotlin
	 * logger trace "进入方法 handleRequest"
	 * ```
	 *
	 * @param string 消息对象；允许为 `null`。
	 */
	infix fun trace(string: Any?) {
		trace("{}", string)
	}

	/**
	 * 以中缀写法记录一条带异常堆栈的 TRACE 日志。
	 *
	 * 等价于 `trace("{}", t?.message, t)`：异常消息作为日志文本，[t] 本身作为
	 * 异常随日志输出。`t` 为 `null` 时没有堆栈，日志文本为 `null`。
	 *
	 * ```kotlin
	 * logger trace exception
	 * ```
	 *
	 * @param t 要记录的异常；允许为 `null`。
	 */
	infix fun trace(t: Throwable?) {
		trace("{}", t?.message, t)
	}

	/**
	 * 以中缀写法记录一条 WARN 日志。
	 *
	 * [string] 作为 `{}` 占位符参数传给底层日志，等价于：
	 * ```kotlin
	 * warn("{}", string)
	 * ```
	 * 底层会调用 [string] 的 `toString()` 填充占位符；传 `null` 时显示 `null`。
	 *
	 * ```kotlin
	 * logger warn "连接池使用率超过 80%"
	 * ```
	 *
	 * @param string 消息对象；允许为 `null`。
	 */
	infix fun warn(string: Any?) {
		warn("{}", string)
	}

	/**
	 * 以中缀写法记录一条带异常堆栈的 WARN 日志。
	 *
	 * 等价于 `warn("{}", t?.message, t)`：异常消息作为日志文本，[t] 本身作为
	 * 异常随日志输出。`t` 为 `null` 时没有堆栈，日志文本为 `null`。
	 *
	 * ```kotlin
	 * logger warn exception
	 * ```
	 *
	 * @param t 要记录的异常；允许为 `null`。
	 */
	infix fun warn(t: Throwable?) {
		warn("{}", t?.message, t)
	}

	/**
	 * 以中缀写法记录一条 ERROR 日志。
	 *
	 * [string] 作为 `{}` 占位符参数传给底层日志，等价于：
	 * ```kotlin
	 * error("{}", string)
	 * ```
	 * 底层会调用 [string] 的 `toString()` 填充占位符；传 `null` 时显示 `null`。
	 *
	 * ```kotlin
	 * logger error "支付回调验签失败"
	 * ```
	 *
	 * @param string 消息对象；允许为 `null`。
	 */
	infix fun error(string: Any?) {
		error("{}", string)
	}

	/**
	 * 以中缀写法记录一条带异常堆栈的 ERROR 日志。
	 *
	 * 等价于 `error("{}", t?.message, t)`：异常消息作为日志文本，[t] 本身作为
	 * 异常随日志输出。`t` 为 `null` 时没有堆栈，日志文本为 `null`。
	 *
	 * ```kotlin
	 * logger error exception
	 * ```
	 *
	 * @param t 要记录的异常；允许为 `null`。
	 */
	infix fun error(t: Throwable?) {
		error("{}", t?.message, t)
	}

	/**
	 * 返回底层 Logger 的名称，即构造时传入的名称或类的完全限定名。
	 *
	 * @return Logger 名称。
	 */
	override fun getName(): String {
		return logger.name
	}
	
	/**
	 * 判断 TRACE 级别是否启用（不带 Marker）。
	 *
	 * @return 启用返回 `true`。
	 */
	override fun isTraceEnabled(): Boolean {
		return logger.isTraceEnabled
	}
	
	/**
	 * 判断 TRACE 级别在指定 [Marker] 下是否启用。
	 *
	 * @param marker 用于过滤的标记。
	 * @return 启用返回 `true`。
	 */
	override fun isTraceEnabled(marker: Marker?): Boolean {
		return logger.isTraceEnabled(marker)
	}
	
	/**
	 * 判断 INFO 级别是否启用（不带 Marker）。
	 *
	 * @return 启用返回 `true`。
	 */
	override fun isInfoEnabled(): Boolean {
		return logger.isInfoEnabled
	}
	
	/**
	 * 判断 INFO 级别在指定 [Marker] 下是否启用。
	 *
	 * @param marker 用于过滤的标记。
	 * @return 启用返回 `true`。
	 */
	override fun isInfoEnabled(marker: Marker?): Boolean {
		return logger.isInfoEnabled(marker)
	}
	
	/**
	 * 判断 WARN 级别是否启用（不带 Marker）。
	 *
	 * @return 启用返回 `true`。
	 */
	override fun isWarnEnabled(): Boolean {
		return logger.isWarnEnabled
	}
	
	/**
	 * 判断 WARN 级别在指定 [Marker] 下是否启用。
	 *
	 * @param marker 用于过滤的标记。
	 * @return 启用返回 `true`。
	 */
	override fun isWarnEnabled(marker: Marker?): Boolean {
		return logger.isWarnEnabled(marker)
	}
	
	/**
	 * 判断 ERROR 级别是否启用（不带 Marker）。
	 *
	 * @return 启用返回 `true`。
	 */
	override fun isErrorEnabled(): Boolean {
		return logger.isErrorEnabled
	}
	
	/**
	 * 判断 ERROR 级别在指定 [Marker] 下是否启用。
	 *
	 * @param marker 用于过滤的标记。
	 * @return 启用返回 `true`。
	 */
	override fun isErrorEnabled(marker: Marker?): Boolean {
		return logger.isErrorEnabled(marker)
	}
	
	/**
	 * 判断 DEBUG 级别是否启用（不带 Marker）。
	 *
	 * @return 启用返回 `true`。
	 */
	override fun isDebugEnabled(): Boolean {
		return logger.isDebugEnabled
	}
	
	/**
	 * 判断 DEBUG 级别在指定 [Marker] 下是否启用。
	 *
	 * @param marker 用于过滤的标记。
	 * @return 启用返回 `true`。
	 */
	override fun isDebugEnabled(marker: Marker?): Boolean {
		return logger.isDebugEnabled(marker)
	}
	
	/**
	 * 以 TRACE 级别记录一条纯消息，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 */
	override fun trace(msg: String?) {
		log(
			Level.TRACE, msg
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带单个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun trace(format: String?, arg: Any?) {
		log(
			Level.TRACE, format, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带两个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun trace(format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.TRACE, format, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun trace(format: String?, vararg arguments: Any?) {
		log(
			Level.TRACE, format, arguments = arguments
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条消息并附带异常，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun trace(msg: String?, t: Throwable?) {
		log(
			Level.TRACE,
			msg,
			t,
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带 [Marker] 的纯消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 */
	override fun trace(marker: Marker?, msg: String?) {
		log(
			Level.TRACE, msg, marker = marker
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带 [Marker] 和单个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun trace(marker: Marker?, format: String?, arg: Any?) {
		log(
			Level.TRACE, format, marker = marker, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带 [Marker] 和两个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.TRACE, format, marker = marker, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带 [Marker] 和可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param argArray 替换占位符的参数数组。
	 */
	override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {
		log(
			Level.TRACE, format, marker = marker, arguments = argArray
		)
	}
	
	/**
	 * 以 TRACE 级别记录一条带 [Marker] 的消息并附带异常，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun trace(marker: Marker?, msg: String?, t: Throwable?) {
		log(
			Level.TRACE, msg, t, marker
		)
	}
	
	/**
	 * 以 DEBUG 级别记录一条纯消息，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 */
	override fun debug(msg: String?) {
		log(Level.DEBUG, msg)
	}
	
	/**
	 * 以 DEBUG 级别记录一条带单个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun debug(format: String?, arg: Any?) {
		log(Level.DEBUG, format, arguments = arrayOf(arg))
	}
	
	/**
	 * 以 DEBUG 级别记录一条带两个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun debug(format: String?, arg1: Any?, arg2: Any?) {
		log(Level.DEBUG, format, arguments = arrayOf(arg1, arg2))
	}
	
	/**
	 * 以 DEBUG 级别记录一条带可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun debug(format: String?, vararg arguments: Any?) {
		log(Level.DEBUG, format, arguments = arguments)
	}
	
	/**
	 * 以 DEBUG 级别记录一条消息并附带异常，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun debug(msg: String?, t: Throwable?) {
		log(Level.DEBUG, msg, t)
	}
	
	/**
	 * 以 DEBUG 级别记录一条带 [Marker] 的纯消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 */
	override fun debug(marker: Marker?, msg: String?) {
		log(Level.DEBUG, msg, marker = marker)
	}
	
	/**
	 * 以 DEBUG 级别记录一条带 [Marker] 和单个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun debug(marker: Marker?, format: String?, arg: Any?) {
		log(
			Level.DEBUG, format, marker = marker, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 DEBUG 级别记录一条带 [Marker] 和两个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.DEBUG, format, marker = marker, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 DEBUG 级别记录一条带 [Marker] 和可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {
		log(
			Level.DEBUG, format, marker = marker, arguments = arguments
		)
	}
	
	/**
	 * 以 DEBUG 级别记录一条带 [Marker] 的消息并附带异常，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
		log(
			Level.DEBUG, msg, t, marker
		)
	}
	
	/**
	 * 以 INFO 级别记录一条纯消息，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 */
	override fun info(msg: String?) {
		log(
			Level.INFO, msg
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带单个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun info(format: String?, arg: Any?) {
		log(
			Level.INFO, format, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带两个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun info(format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.INFO, format, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun info(format: String?, vararg arguments: Any?) {
		log(
			Level.INFO, format, arguments = arguments
		)
	}
	
	/**
	 * 以 INFO 级别记录一条消息并附带异常，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun info(msg: String?, t: Throwable?) {
		log(
			Level.INFO, msg, t
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带 [Marker] 的纯消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 */
	override fun info(marker: Marker?, msg: String?) {
		log(
			Level.INFO, msg, marker = marker
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带 [Marker] 和单个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun info(marker: Marker?, format: String?, arg: Any?) {
		log(
			Level.INFO, format, marker = marker, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带 [Marker] 和两个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.INFO, format, marker = marker, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带 [Marker] 和可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {
		log(
			Level.INFO, format, marker = marker, arguments = arguments
		)
	}
	
	/**
	 * 以 INFO 级别记录一条带 [Marker] 的消息并附带异常，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun info(marker: Marker?, msg: String?, t: Throwable?) {
		log(
			Level.INFO, msg, t, marker
		)
	}
	
	/**
	 * 以 WARN 级别记录一条纯消息，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 */
	override fun warn(msg: String?) {
		log(
			Level.WARN, msg
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带单个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun warn(format: String?, arg: Any?) {
		log(
			Level.WARN, format, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun warn(format: String?, vararg arguments: Any?) {
		log(
			Level.WARN, format, arguments = arguments
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带两个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun warn(format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.WARN, format, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 WARN 级别记录一条消息并附带异常，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun warn(msg: String?, t: Throwable?) {
		log(
			Level.WARN, msg, t
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带 [Marker] 的纯消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 */
	override fun warn(marker: Marker?, msg: String?) {
		log(
			Level.WARN, msg, marker = marker
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带 [Marker] 和单个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun warn(marker: Marker?, format: String?, arg: Any?) {
		log(
			Level.WARN, format, marker = marker, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带 [Marker] 和两个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.WARN, format, marker = marker, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带 [Marker] 和可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {
		log(
			Level.WARN, format, marker = marker, arguments = arguments
		)
	}
	
	/**
	 * 以 WARN 级别记录一条带 [Marker] 的消息并附带异常，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
		log(
			Level.WARN, msg, t, marker
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条纯消息，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 */
	override fun error(msg: String?) {
		log(
			Level.ERROR, msg
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带单个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun error(format: String?, arg: Any?) {
		log(
			Level.ERROR, format, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带两个占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun error(format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.ERROR, format, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun error(format: String?, vararg arguments: Any?) {
		log(
			Level.ERROR, format, arguments = arguments
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条消息并附带异常，委托给 [log]。
	 *
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun error(msg: String?, t: Throwable?) {
		log(
			Level.ERROR, msg, t
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带 [Marker] 的纯消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 */
	override fun error(marker: Marker?, msg: String?) {
		log(
			Level.ERROR, msg, marker = marker
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带 [Marker] 和单个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arg 替换占位符的参数。
	 */
	override fun error(marker: Marker?, format: String?, arg: Any?) {
		log(
			Level.ERROR, format, marker = marker, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带 [Marker] 和两个占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含两个 `{}` 占位符的消息模板。
	 * @param arg1 第一个占位符参数。
	 * @param arg2 第二个占位符参数。
	 */
	override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
		log(
			Level.ERROR, format, marker = marker, arguments = arrayOf(arg1, arg2)
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带 [Marker] 和可变数量占位符参数的消息，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param format 含 `{}` 占位符的消息模板。
	 * @param arguments 替换占位符的参数数组。
	 */
	override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {
		log(
			Level.ERROR, format, marker = marker, arguments = arguments
		)
	}
	
	/**
	 * 以 ERROR 级别记录一条带 [Marker] 的消息并附带异常，委托给 [log]。
	 *
	 * @param marker 日志标记。
	 * @param msg 日志消息。
	 * @param t 关联的异常对象。
	 */
	override fun error(marker: Marker?, msg: String?, t: Throwable?) {
		log(
			Level.ERROR, msg, t, marker
		)
	}
	
	/**
	 * 核心日志入口（惰性求值形式），供便捷方法委托。
	 *
	 * 只有对应级别启用时才会调用 [caller] 构造消息；级别关闭时消息构造开销被
	 * 完全避免。底层支持 [LocationAwareLogger] 时，通过 `FQCN` 跳过本类栈帧；
	 * 否则使用 SLF4J 2.x 的 [Logger.atLevel] 构建器，其 `log(Supplier)` 重载
	 * 本身就是惰性的。
	 *
	 * 注意：位置感知分支目前只按 [level] 判断是否启用，不区分 [marker]；
	 * 如果需要按 Marker 精确控制惰性求值，可先使用底层 Logger 的
	 * `isXxxEnabled(marker)` 判断。
	 *
	 * @param level 日志级别，默认 [Level.DEBUG]。
	 * @param throwable 关联异常，会随日志输出完整堆栈。
	 * @param marker 日志标记。
	 * @param caller 消息提供函数，仅在级别启用时调用。
	 */
	internal fun log(
		level: Level = Level.DEBUG, throwable: Throwable? = null, marker: Marker? = null, caller: () -> String
	) {
		if (isLocationAwareLogger) {
			if (isEnabled(level)) locationAwareLogger?.log(
				marker, FQCN, level.toInt(), caller(), null, throwable
			)
		} else {
			logger.atLevel(level).addMarker(marker).setCause(throwable).log(
				caller
			)
		}
	}
	
	/**
	 * 判断指定 [Level] 在当前日志器上是否启用。
	 *
	 * 等价于对应级别的 `isXxxEnabled`，不包含 [Marker] 判断。该方法供惰性日志
	 * 方法和调用方在构造消息前做前置判断。
	 *
	 * @param level 日志级别。
	 * @return 该级别启用返回 `true`。
	 */
	fun isEnabled(level: Level): Boolean = when (level) {
		Level.ERROR -> isErrorEnabled
		Level.WARN -> isWarnEnabled
		Level.INFO -> isInfoEnabled
		Level.DEBUG -> isDebugEnabled
		Level.TRACE -> isTraceEnabled
	}
	
	/**
	 * 以 INFO 级别记录任意对象。
	 *
	 * 等价于 `info("{}", arg, throwable)`：对象被作为**单个占位符参数**传入，
	 * 由底层实现调用其 `toString()` 填充 `{}`。若 [arg] 为 `null`，输出为 `"null"`。
	 *
	 * 单参数调用由内置的 infix `info(Any?)` 提供（Java 侧同样可见 `info(Object)`），
	 * 本方法保留默认 [throwable] 参数，便于显式传入异常。
	 *
	 * @param arg 作为消息内容输出的对象。
	 * @param throwable 关联的异常对象；默认 `null`。
	 */
	fun info(arg: Any?, throwable: Throwable? = null) {
		log(
			Level.INFO, "{}", throwable = throwable, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 INFO 级别记录**惰性求值**的消息。
	 *
	 * 仅当 INFO 级别启用时才会调用 [caller] 构造消息。适合消息构造有可观开销
	 * （字符串拼接、序列化、外部调用等）的场景。
	 *
	 * @param caller 消息提供函数，仅在级别启用时被调用。
	 */
	fun info(caller: () -> String) {
		log(
			Level.INFO
		) {
			caller()
		}
	}
	
	/**
	 * 以 INFO 级别记录**惰性求值**的消息并附带异常。
	 *
	 * 仅当 INFO 级别启用时才会调用 [caller]。异常对象会随日志输出完整堆栈。
	 *
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数，仅在级别启用时被调用。
	 */
	fun info(throwable: Throwable?, caller: () -> String) {
		log(
			Level.INFO, throwable
		) {
			caller()
		}
	}
	
	/**
	 * 以 INFO 级别记录**惰性求值**的消息，并附带 [Marker] 与异常。
	 *
	 * 仅当 INFO 级别在给定 [marker] 下启用时才会调用 [caller]。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数，仅在级别启用时被调用。
	 */
	fun info(marker: Marker?, throwable: Throwable?, caller: () -> String) {
		log(
			Level.INFO, throwable, marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 INFO 级别记录**惰性求值**的消息，并附带 [Marker]。
	 *
	 * 仅当 INFO 级别在给定 [marker] 下启用时才会调用 [caller]。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param caller 消息提供函数，仅在级别启用时被调用。
	 */
	fun info(marker: Marker?, caller: () -> String) {
		log(
			Level.INFO, marker = marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 WARN 级别记录任意对象，等价于 `warn("{}", arg, throwable)`。
	 *
	 * @param arg 作为消息内容输出的对象。
	 * @param throwable 关联的异常对象；默认 `null`。
	 * @see info
	 */
	fun warn(arg: Any?, throwable: Throwable? = null) {
		log(
			Level.WARN, "{}", throwable = throwable, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 WARN 级别记录**惰性求值**的消息，仅在 WARN 级别启用时调用 [caller]。
	 *
	 * @param caller 消息提供函数。
	 */
	fun warn(caller: () -> String) {
		log(
			Level.WARN
		) {
			caller()
		}
	}
	
	/**
	 * 以 WARN 级别记录**惰性求值**的消息并附带异常，仅在 WARN 级别启用时调用 [caller]。
	 *
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun warn(throwable: Throwable?, caller: () -> String) {
		log(
			Level.WARN, throwable
		) {
			caller()
		}
	}
	
	/**
	 * 以 WARN 级别记录**惰性求值**的消息，附带 [Marker] 与异常。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun warn(marker: Marker?, throwable: Throwable?, caller: () -> String) {
		log(
			Level.WARN, throwable, marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 WARN 级别记录**惰性求值**的消息，附带 [Marker]。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun warn(marker: Marker?, caller: () -> String) {
		log(
			Level.WARN, marker = marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 ERROR 级别记录任意对象，等价于 `error("{}", arg, throwable)`。
	 *
	 * @param arg 作为消息内容输出的对象。
	 * @param throwable 关联的异常对象；默认 `null`。
	 * @see info
	 */
	fun error(arg: Any?, throwable: Throwable? = null) {
		log(
			Level.ERROR, "{}", throwable = throwable, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 ERROR 级别记录**惰性求值**的消息，仅在 ERROR 级别启用时调用 [caller]。
	 *
	 * @param caller 消息提供函数。
	 */
	fun error(caller: () -> String) {
		log(
			Level.ERROR
		) {
			caller()
		}
	}
	
	/**
	 * 以 ERROR 级别记录**惰性求值**的消息并附带异常，仅在 ERROR 级别启用时调用 [caller]。
	 *
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun error(throwable: Throwable?, caller: () -> String) {
		log(
			Level.ERROR, throwable
		) {
			caller()
		}
	}
	
	/**
	 * 以 ERROR 级别记录**惰性求值**的消息，附带 [Marker] 与异常。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun error(marker: Marker?, throwable: Throwable?, caller: () -> String) {
		log(
			Level.ERROR, throwable, marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 ERROR 级别记录**惰性求值**的消息，附带 [Marker]。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun error(marker: Marker?, caller: () -> String) {
		log(
			Level.ERROR, marker = marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 DEBUG 级别记录任意对象，等价于 `debug("{}", arg, throwable)`。
	 *
	 * @param arg 作为消息内容输出的对象。
	 * @param throwable 关联的异常对象；默认 `null`。
	 * @see info
	 */
	fun debug(arg: Any?, throwable: Throwable? = null) {
		log(
			Level.DEBUG, "{}", throwable = throwable, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 DEBUG 级别记录**惰性求值**的消息，仅在 DEBUG 级别启用时调用 [caller]。
	 *
	 * @param caller 消息提供函数。
	 */
	fun debug(caller: () -> String) {
		log(
			Level.DEBUG
		) {
			caller()
		}
	}
	
	/**
	 * 以 DEBUG 级别记录**惰性求值**的消息并附带异常，仅在 DEBUG 级别启用时调用 [caller]。
	 *
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun debug(throwable: Throwable?, caller: () -> String) {
		log(
			Level.DEBUG, throwable
		) {
			caller()
		}
	}
	
	/**
	 * 以 DEBUG 级别记录**惰性求值**的消息，附带 [Marker] 与异常。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun debug(marker: Marker?, throwable: Throwable?, caller: () -> String) {
		log(
			Level.DEBUG, throwable, marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 DEBUG 级别记录**惰性求值**的消息，附带 [Marker]。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun debug(marker: Marker?, caller: () -> String) {
		log(
			Level.DEBUG, marker = marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 TRACE 级别记录任意对象，等价于 `trace("{}", arg, throwable)`。
	 *
	 * @param arg 作为消息内容输出的对象。
	 * @param throwable 关联的异常对象；默认 `null`。
	 * @see info
	 */
	fun trace(arg: Any?, throwable: Throwable? = null) {
		log(
			Level.TRACE, "{}", throwable = throwable, arguments = arrayOf(arg)
		)
	}
	
	/**
	 * 以 TRACE 级别记录**惰性求值**的消息，仅在 TRACE 级别启用时调用 [caller]。
	 *
	 * @param caller 消息提供函数。
	 */
	fun trace(caller: () -> String) {
		log(
			Level.TRACE
		) {
			caller()
		}
	}
	
	/**
	 * 以 TRACE 级别记录**惰性求值**的消息并附带异常，仅在 TRACE 级别启用时调用 [caller]。
	 *
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun trace(throwable: Throwable?, caller: () -> String) {
		log(
			Level.TRACE, throwable
		) {
			caller()
		}
	}
	
	/**
	 * 以 TRACE 级别记录**惰性求值**的消息，附带 [Marker] 与异常。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param throwable 关联的异常对象，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun trace(marker: Marker?, throwable: Throwable?, caller: () -> String) {
		log(
			Level.TRACE, throwable, marker
		) {
			caller()
		}
	}
	
	/**
	 * 以 TRACE 级别记录**惰性求值**的消息，附带 [Marker]。
	 *
	 * @param marker 日志标记，可为 `null`。
	 * @param caller 消息提供函数。
	 */
	fun trace(marker: Marker?, caller: () -> String) {
		log(
			Level.TRACE, marker = marker
		) {
			caller()
		}
	}
}
