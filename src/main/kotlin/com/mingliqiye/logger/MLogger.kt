package com.mingliqiye.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger

/**
 * 对 SLF4J [Logger] 的包装类，在保留标准日志 API 的同时提供两类增强能力：
 *
 * 1. **位置感知（Location Aware）**：当底层实现支持 [LocationAwareLogger] 时，
 *    日志的位置信息（类名、方法名、行号）会穿过本包装类，定位到真正调用
 *    `info` / `debug` 等方法的**业务代码**，而不是停留在 `MLogger` 内部。
 * 2. **便捷 API**：除实现 [Logger] 的全部标准方法外，额外提供
 *    - 接收任意对象（`Any?`）作为消息的方法，内部以 `"{}"` 占位符格式化；
 *    - 接收 `() -> String` 的**惰性求值**方法，仅在对应级别启用时才构造消息；
 *    - 上述方法均支持可选的 [Throwable] 与 [Marker] 重载。
 *
 * ### 实例获取
 * 本类的构造函数为 `internal`，应通过 [Loggers] 获取实例（带缓存，同名唯一）：
 * ```kotlin
 * val logger = Loggers.getLogger(MyService::class)
 * logger.info("user {} logged in", userId)
 * logger.debug { "expensive: " + buildDetail() }   // 级别关闭时不会执行
 * ```
 *
 * ### 位置定位的前提
 * 位置感知依赖本类 `companion object` 中的 `FQCN` 常量与 [LocationAwareLogger.log]
 * 的协作。若底层实现不支持 [LocationAwareLogger]（例如纯 NOP 实现），会退化为
 * 普通 `atLevel(...)` 调用，此时日志位置将停在本包装类上，这是 SLF4J 的固有限制。
 *
 * @see Loggers
 * @see LocationAwareLogger
 */
class MLogger : Logger {
	
	/**
	 * 本类共用的常量区。
	 *
	 * 把与实例无关、整个类只需一份的常量放在 `companion object` 中，避免每个
	 * [MLogger] 实例都持有一份引用。
	 */
	companion object {
		/**
		 * 传给 [LocationAwareLogger.log] 的“跳过边界”完全限定类名（FQCN）。
		 *
		 * 值为 `MLogger::class.java.name`，即 `"com.mingliqiye.logger.MLogger"`。
		 * 底层实现（如 Logback）在分析调用栈时，会跳过类名等于此值的栈帧，
		 * 再跳过日志框架自身的帧，从而把日志位置定位到真正的业务调用者。
		 *
		 * ### 为什么用 `::class.java.name` 而不是硬编码字符串
		 * 硬编码字符串在类被重命名或移动包后不会自动更新，日志位置会悄悄回退到
		 * 包装层，且很难察觉。用 `MLogger::class.java.name` 可以让值自动跟随类名，
		 * 重构时不会脱节。
		 *
		 * ### 唯一性
		 * 本常量是整个项目中 `MLogger` 的 FQCN 的**唯一来源**。[Loggers] 不再
		 * 维护自己的 FQCN 常量，避免类重命名时两处定义不同步的问题。
		 */
		private val FQCN: String = MLogger::class.java.name
		
		
		/**
		 * 以中缀语法记录一条 DEBUG 级别日志。
		 *
		 * 内部等价于 `this.debug("{}", string)`，把 [string] 作为消息模板 `{}` 的
		 * 占位符参数输出。注意：[string] 不是直接的消息文本，而是占位符参数。
		 *
		 * 使用示例：
		 * ```
		 * logger debug "用户登录成功"
		 * ```
		 *
		 * @param string 要记录的对象，通过 `toString()` 转为字符串后填充 `{}` 占位符；
		 *               为 `null` 时输出字符串 `"null"`。
		 */
		infix fun MLogger.debug(string: Any?) {
			this.debug("{}", string as Any?)
		}
		
		/**
		 * 以中缀语法记录一条带异常堆栈的 DEBUG 级别日志。
		 *
		 * 内部等价于 `this.debug("{}", t?.message, t)`，把异常消息作为 `{}` 占位符
		 * 参数输出，并把 [t] 作为异常堆栈一并记录。
		 *
		 * 使用示例：
		 * ```
		 * logger debug exception
		 * ```
		 *
		 * @param t 要记录的异常对象；`null` 时异常消息为 `"null"`，堆栈为空。
		 */
		infix fun MLogger.debug(t: Throwable?) {
			this.debug("{}", t?.message, t)
		}
		
		/**
		 * 以中缀语法记录一条 INFO 级别日志。
		 *
		 * 内部等价于 `this.info("{}", string)`，把 [string] 作为消息模板 `{}` 的
		 * 占位符参数输出。注意：[string] 不是直接的消息文本，而是占位符参数。
		 *
		 * 使用示例：
		 * ```
		 * logger info "订单创建完成"
		 * ```
		 *
		 * @param string 要记录的对象，通过 `toString()` 转为字符串后填充 `{}` 占位符；
		 *               为 `null` 时输出字符串 `"null"`。
		 */
		infix fun MLogger.info(string: Any?) {
			this.info("{}", string as Any?)
		}
		
		/**
		 * 以中缀语法记录一条带异常堆栈的 INFO 级别日志。
		 *
		 * 内部等价于 `this.info("{}", t?.message, t)`，把异常消息作为 `{}` 占位符
		 * 参数输出，并把 [t] 作为异常堆栈一并记录。
		 *
		 * 使用示例：
		 * ```
		 * logger info exception
		 * ```
		 *
		 * @param t 要记录的异常对象；`null` 时异常消息为 `"null"`，堆栈为空。
		 */
		infix fun MLogger.info(t: Throwable?) {
			this.info("{}", t?.message, t)
		}
		
		/**
		 * 以中缀语法记录一条 TRACE 级别日志。
		 *
		 * 内部等价于 `this.trace("{}", string)`，把 [string] 作为消息模板 `{}` 的
		 * 占位符参数输出。注意：[string] 不是直接的消息文本，而是占位符参数。
		 *
		 * 使用示例：
		 * ```
		 * logger trace "进入方法 handleRequest"
		 * ```
		 *
		 * @param string 要记录的对象，通过 `toString()` 转为字符串后填充 `{}` 占位符；
		 *               为 `null` 时输出字符串 `"null"`。
		 */
		infix fun MLogger.trace(string: Any?) {
			this.trace("{}", string as Any?)
		}
		
		/**
		 * 以中缀语法记录一条带异常堆栈的 TRACE 级别日志。
		 *
		 * 内部等价于 `this.trace("{}", t?.message, t)`，把异常消息作为 `{}` 占位符
		 * 参数输出，并把 [t] 作为异常堆栈一并记录。
		 *
		 * 使用示例：
		 * ```
		 * logger trace exception
		 * ```
		 *
		 * @param t 要记录的异常对象；`null` 时异常消息为 `"null"`，堆栈为空。
		 */
		infix fun MLogger.trace(t: Throwable?) {
			this.trace("{}", t?.message, t)
		}
		
		/**
		 * 以中缀语法记录一条 ERROR 级别日志。
		 *
		 * 内部等价于 `this.error("{}", string)`，把 [string] 作为消息模板 `{}` 的
		 * 占位符参数输出。注意：[string] 不是直接的消息文本，而是占位符参数。
		 *
		 * 使用示例：
		 * ```
		 * logger error "支付回调验签失败"
		 * ```
		 *
		 * @param string 要记录的对象，通过 `toString()` 转为字符串后填充 `{}` 占位符；
		 *               为 `null` 时输出字符串 `"null"`。
		 */
		infix fun MLogger.error(string: Any?) {
			this.error("{}", string as Any?)
		}
		
		/**
		 * 以中缀语法记录一条带异常堆栈的 ERROR 级别日志。
		 *
		 * 内部等价于 `this.error("{}", t?.message, t)`，把异常消息作为 `{}` 占位符
		 * 参数输出，并把 [t] 作为异常堆栈一并记录。
		 *
		 * 使用示例：
		 * ```
		 * logger error exception
		 * ```
		 *
		 * @param t 要记录的异常对象；`null` 时异常消息为 `"null"`，堆栈为空。
		 */
		infix fun MLogger.error(t: Throwable?) {
			this.error("{}", t?.message, t)
		}
		
		/**
		 * 以中缀语法记录一条 WARN 级别日志。
		 *
		 * 内部等价于 `this.warn("{}", string)`，把 [string] 作为消息模板 `{}` 的
		 * 占位符参数输出。注意：[string] 不是直接的消息文本，而是占位符参数。
		 *
		 * 使用示例：
		 * ```
		 * logger warn "连接池使用率超过 80%"
		 * ```
		 *
		 * @param string 要记录的对象，通过 `toString()` 转为字符串后填充 `{}` 占位符；
		 *               为 `null` 时输出字符串 `"null"`。
		 */
		infix fun MLogger.warn(string: Any?) {
			this.warn("{}", string as Any?)
		}
		
		/**
		 * 以中缀语法记录一条带异常堆栈的 WARN 级别日志。
		 *
		 * 内部等价于 `this.warn("{}", t?.message, t)`，把异常消息作为 `{}` 占位符
		 * 参数输出，并把 [t] 作为异常堆栈一并记录。
		 *
		 * 使用示例：
		 * ```
		 * logger warn exception
		 * ```
		 *
		 * @param t 要记录的异常对象；`null` 时异常消息为 `"null"`，堆栈为空。
		 */
		infix fun MLogger.warn(t: Throwable?) {
			this.warn("{}", t?.message, t)
		}
	}
	
	/**
	 * 底层 SLF4J [Logger]，所有标准日志方法的最终委托目标。
	 *
	 * 在构造时通过 [LoggerFactory.getLogger] 创建，之后不再变化。
	 */
	private val logger: Logger
	
	/**
	 * 底层 [Logger] 对应的 [LocationAwareLogger] 视图；若底层实现不支持则为 `null`。
	 *
	 * 非 `null` 时，[log] 会走位置感知路径，把 `FQCN` 作为“跳过边界”
	 * 传给底层实现，从而让日志位置穿过本包装类。
	 */
	private val locationAwareLogger: LocationAwareLogger?
	
	/**
	 * [locationAwareLogger] 是否非 `null` 的布尔快照，用于在 [log] 热路径中避免
	 * 反复进行 `null` 判断。
	 *
	 * 该字段只在构造函数中赋值一次，之后不再变化。语义上应为 `val`，当前声明为
	 * `var` 仅因为在 `apply { ... }` 块里被赋值，编译器无法在字段声明处完成初始化。
	 * 可改为在构造体内用局部变量显式判断，把字段本身声明为 `val`：
	 * ```kotlin
	 * val la = logger as? LocationAwareLogger
	 * locationAwareLogger = la
	 * isLocationAwareLogger = la != null
	 * ```
	 */
	private var isLocationAwareLogger: Boolean = false
	
	/**
	 * 核心日志入口（占位符 + 可变参数形式）。
	 *
	 * 根据底层实现是否支持 [LocationAwareLogger]，选择两条路径之一：
	 * - **支持**：调用 [LocationAwareLogger.log]，并传入 `FQCN` 作为调用边界，
	 *   使日志位置穿过本类，定位到业务调用者。
	 * - **不支持**：退化为 SLF4J 2.x 的 [Logger.atLevel] 构建器，
	 *   依次附加 [Marker]、[Throwable]，再以 `msg` 和 [arguments] 记录。
	 *
	 * ### 参数与 SLF4J 占位符
	 * [msg] 中的 `{}` 会被 [arguments] 中的元素依次替换，规则与 SLF4J 原生一致。
	 * 若 [msg] 为 `null`，不同实现的行为可能略有差异，调用方应避免传 `null`。
	 *
	 * ### 默认参数与 `$default`
	 * 本方法声明了多个默认参数，Kotlin 编译器会生成一个 `log$default` 合成方法。
	 * 这不会影响位置感知，因为 Logback 会跳过所有类名为 `FQCN` 的栈帧
	 * （包括 `log$default`）。但如果你在其他类里以“只传部分参数”的方式调用它，
	 * 栈帧里会多出一帧 `log$default`，排查调用栈时需留意。
	 *
	 * ### `?.` 与 [isLocationAwareLogger] 的冗余
	 * 外层已经判断了 [isLocationAwareLogger]，理论上 [locationAwareLogger] 必然非 `null`，
	 * 这里的 `?.` 是防御性冗余。建议二选一：要么去掉 `?.`，要么去掉
	 * [isLocationAwareLogger] 判断、直接依赖 `?.`，避免读者误以为二者可能不一致。
	 *
	 * @param level 日志级别，默认 [Level.DEBUG]。
	 * @param msg 日志消息，可包含 `{}` 占位符；默认 `null`。
	 * @param throwable 关联的异常对象，会随日志输出完整堆栈；默认 `null`。
	 * @param marker 日志标记，用于超越级别的过滤与路由；默认 `null`。
	 * @param arguments 用于替换 [msg] 中 `{}` 的参数；默认空数组。
	 */
	internal fun log(
		level: Level = Level.DEBUG,
		msg: String? = null,
		throwable: Throwable? = null,
		marker: Marker? = null,
		vararg arguments: Any? = emptyArray()
	) {
		if (isLocationAwareLogger) {
			locationAwareLogger?.log(
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
	 * 通常不应直接调用（构造函数为 `internal`），请使用 [Loggers.getLogger] 以获得
	 * 缓存与进程内唯一性。
	 *
	 * 本构造器与 [MLogger] 的 [Class] 构造器在 [Loggers] 的缓存中共享同一 key
	 * （`name` 与 `clazz.name` 相同时命中同一条目），因此两者创建的实例必须行为等价。
	 * 若未来在此构造器中引入额外逻辑，需要重新评估缓存 key 策略。
	 *
	 * @param name Logger 名称，通常为类的完全限定名或自定义标识。
	 */
	internal constructor(name: String) {
		logger = LoggerFactory.getLogger(name)
		locationAwareLogger = (logger as? LocationAwareLogger)?.apply {
			isLocationAwareLogger = true
		}
	}
	
	/**
	 * 按类型创建 [MLogger]。
	 *
	 * 通常不应直接调用（构造函数为 `internal`），请使用 [Loggers.getLogger] 以获得
	 * 缓存与进程内唯一性。Logger 名称取自 [Class.getName]。
	 *
	 * 本构造器与 [MLogger] 的 [String] 构造器在 [Loggers] 的缓存中共享同一 key
	 * （`clazz.name` 与 `name` 相同时命中同一条目），因此两者创建的实例必须行为等价。
	 * 若未来在此构造器中引入依赖 [Class] 对象本身的逻辑，需要重新评估缓存 key 策略。
	 *
	 * @param clazz 用于推断 Logger 名称的类。
	 */
	internal constructor(clazz: Class<*>) {
		logger = LoggerFactory.getLogger(clazz)
		locationAwareLogger = (logger as? LocationAwareLogger)?.apply {
			isLocationAwareLogger = true
		}
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
	 * 核心日志入口（**惰性求值**形式）。
	 *
	 * 与另一个 [log] 重载的区别在于：消息由 [caller] 函数在**确认需要记录时**才求值。
	 * 当 [isLocationAwareLogger] 为 `true` 时，只有 [isEnabled] 返回 `true` 才会调用
	 * [caller]；否则消息构造的开销被完全避免。非位置感知分支使用 SLF4J 2.x 的
	 * [Logger.atLevel] 构建器，其 `log(Supplier)` 重载本身也是惰性的。
	 *
	 * ### 默认参数与 `$default`
	 * 本方法同样声明了默认参数，会生成 `log$default` 合成方法。位置感知路径下
	 * Logback 会跳过所有类名为 `FQCN` 的栈帧，因此不影响定位；但排查
	 * 调用栈时可能看到 `log$default` 帧。
	 *
	 * ### 与另一个 [log] 重载的取舍
	 * 本重载的 [caller] 没有占位符参数，适合“消息本身就是一次昂贵构造”的场景。
	 * 如果消息需要 `{}` 格式化参数，请使用另一个重载。
	 *
	 * @param level 日志级别，默认 [Level.DEBUG]。
	 * @param throwable 关联的异常对象，会随日志输出完整堆栈；默认 `null`。
	 * @param marker 日志标记，用于超越级别的过滤与路由；默认 `null`。
	 * @param caller 消息提供函数，仅在级别启用时被调用。
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
	 * 判断指定 [Level] 是否启用。
	 *
	 * 内部按 [Level] 分派到对应的 `isXxxEnabled` 方法，供 [log]（惰性求值重载）
	 * 在调用 [caller] 之前做前置判断。
	 *
	 * @param level 待判断的日志级别。
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
	 * 由于带 [JvmOverloads]，Java 侧会生成 `info(Object)` 和 `info(Object, Throwable)`
	 * 两个重载，便于 Java 调用方省略 [throwable]。
	 *
	 * @param arg 作为消息内容输出的对象。
	 * @param throwable 关联的异常对象；默认 `null`。
	 */
	@JvmOverloads
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
	@JvmOverloads
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
	@JvmOverloads
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
	@JvmOverloads
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
	@JvmOverloads
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
