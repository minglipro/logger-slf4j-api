package com.mingliqiye.logger

import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * [MLogger] 的全局工厂与缓存入口。
 *
 * 按名称或类型获取 [MLogger]，并保证同一个名称在进程内只创建一个包装实例。
 * `getLogger("a.B")`、`getLogger(A::class)`、`getLogger<A>()` 在名称一致时
 * 返回同一个实例。
 *
 * ### 线程安全
 * 缓存通过 [ConcurrentHashMap.computeIfAbsent] 创建，同一 key 的映射函数最多
 * 执行一次，因此本对象的所有公开方法都可以被多线程安全调用。
 *
 * ### 典型用法
 * ```kotlin
 * // 按类获取（推荐，Logger 名即类的完全限定名）
 * val logger = Loggers.getLogger(MyService::class)
 *
 * // 按名称获取
 * val named = Loggers.getLogger("com.example.custom")
 *
 * // reified 便捷写法
 * val reified = Loggers.getLogger<MyService>()
 *
 * // 零参数写法：自动解析调用者类
 * class MyService {
 *     private val log = Loggers.getLogger()
 * }
 * ```
 *
 * ### Java 用法
 * ```java
 * MLogger logger = Loggers.getLogger(MyService.class);
 * ```
 *
 * ### 名称建议
 * 推荐使用类或稳定的模块名。不要用租户 ID、请求 ID 等无界值作为名称，否则
 * [loggerCache] 会持续增长。
 *
 * @see MLogger
 */
object Loggers {
	
	/**
	 * 供本对象内部调试缓存使用的日志器。
	 *
	 * 直接构造而不是通过 [getLogger] 获取，避免与 [loggerCache] 的初始化形成递归。
	 */
	private val logger: MLogger = MLogger(Loggers::class.java)
	private val macker: Marker = MarkerFactory.getMarker("Loggers-Init")
	
	/**
	 * 按 Logger 名称缓存 [MLogger] 实例；key 通常是类的完全限定名。
	 *
	 * 初始容量与负载因子只是减少扩容的经验值，实际容量由
	 * [ConcurrentHashMap] 调整为 2 的幂。
	 */
	private val loggerCache = ConcurrentHashMap<String, MLogger>(25, 0.8f)
	
	/**
	 * 按名称从缓存获取 [MLogger]，缓存未命中时创建并写入。
	 *
	 * [clazz] 非空时使用 [Class] 构造器创建包装器，否则使用名称构造器；调用方
	 * 需要保证 `clazz.name == name`。本方法只会由本对象内部的几个重载调用，
	 * 不对外暴露。
	 *
	 * 通过 [ConcurrentHashMap.computeIfAbsent] 创建，保证并发下同一名称只会
	 * 产生一个 [MLogger] 实例。
	 *
	 * @param name 缓存 key，同时也是 Logger 名称。
	 * @param clazz 可选的类；传入时必须与 [name] 对应。
	 * @return 与 [name] 对应的、进程内唯一的 [MLogger] 实例。
	 */
	private fun getLoggerForCache(name: String, clazz: Class<*>?): MLogger {
		logger.debug(macker) { "getLoggerForCache: $name" }
		val cached = loggerCache[name]
		if (cached != null) {
			logger.debug(macker) { "Cache hit: $name" }
			return cached
		}
		return loggerCache.computeIfAbsent(name) {
			logger.debug(macker) { "add Cache: $name" }
			if (clazz == null) MLogger(name) else MLogger(clazz)
		}
	}
	
	/**
	 * 按名称获取或创建 [MLogger]，并写入缓存。
	 *
	 * 使用 `ConcurrentHashMap.computeIfAbsent` 保证并发下同一名称只创建一次实例。
	 * 若缓存中已存在该名称的实例，直接返回；否则调用 [MLogger] 的字符串构造器创建并缓存。
	 *
	 * @param name Logger 名称，通常为类的完全限定名或自定义标识；不能为 `null`。
	 * @return 与 [name] 对应的、进程内唯一的 [MLogger] 实例。
	 */
	private fun getLoggerForCache(name: String): MLogger = getLoggerForCache(name, null)
	
	/**
	 * 按类型获取或创建 [MLogger]，并写入缓存。
	 *
	 * 缓存 key 为 [clazz] 的完全限定名（[Class.getName]），因此
	 * `getLogger(SomeClass::class.java)` 与 `getLogger(SomeClass::class.java.name)`
	 * 会命中同一缓存条目；这里使用 [Class] 构造器创建 [MLogger]，
	 * 与按名称入口保持相同的底层 Logger 获取方式。
	 *
	 * @param clazz 用于推断 Logger 名称的类；不能为 `null`。
	 * @return 与 [clazz] 对应的、进程内唯一的 [MLogger] 实例。
	 */
	private fun getLoggerForCache(clazz: Class<*>): MLogger = getLoggerForCache(clazz.name, clazz)
	
	/**
	 * 按名称获取 [MLogger]。
	 *
	 * 这是最基础的入口：名称完全由调用方决定，适合需要自定义 Logger 名的场景
	 * （如按业务模块、租户或功能命名）。同一名称多次调用返回同一实例。
	 *
	 * @param name Logger 名称；不能为 `null`。
	 * @return 与 [name] 对应的 [MLogger] 实例。
	 * @see getLogger
	 */
	@JvmStatic
	fun getLogger(name: String): MLogger = getLoggerForCache(name)
	
	/**
	 * 按 [Class] 获取 [MLogger]。
	 *
	 * Logger 名称取自 [Class.getName]，即类的完全限定名。这是 Java 侧最常用的入口，
	 * 也便于在日志配置中按包或类精确控制级别。
	 *
	 * @param clazz 用于推断 Logger 名称的类；不能为 `null`。
	 * @return 与 [clazz] 对应的 [MLogger] 实例。
	 */
	@JvmStatic
	fun getLogger(clazz: Class<*>): MLogger = getLoggerForCache(clazz)
	
	/**
	 * 按 [KClass] 获取 [MLogger]。
	 *
	 * Kotlin 侧的便捷重载，内部转换为 [Class] 后复用 [getLogger] 的缓存逻辑，
	 * 因此与对应的 [Class] 重载返回同一实例。
	 *
	 * @param clazz 用于推断 Logger 名称的 Kotlin 类；不能为 `null`。
	 * @return 与 [clazz] 对应的 [MLogger] 实例。
	 */
	@JvmStatic
	fun getLogger(clazz: KClass<*>): MLogger = getLoggerForCache(clazz.java)
	
	/**
	 * 通过 `reified` 类型参数获取 [MLogger]。
	 *
	 * 编译期内联，`T::class.java` 会被替换为具体的类字面量，调用方无需显式传
	 * [Class] 或 [KClass]，写法更简洁：
	 * ```kotlin
	 * val logger = Loggers.getLogger<MyService>()
	 * ```
	 *
	 * 该函数本身不直接访问缓存，而是转调 [getLogger]（[Class] 重载），
	 * 因此缓存语义与其它重载完全一致。
	 *
	 * ### 关于 `@JvmName`
	 * 本函数与无参的 [getLogger]（自动解析调用者）在 JVM 层擦除后签名都是
	 * 无参 `getLogger()`。为规避签名冲突，二者中必须有一个改名。这里给 reified
	 * 版本指定了 `__Kt_inline_reified_T_Any_getLogger`，无参版本保留 `getLogger`。
	 * **这个注解不能删**，否则编译期会报 JVM 签名冲突。
	 *
	 * @param T 用于推断 Logger 名称的类型；必须是非空类型。
	 * @return 与 `T` 对应的 [MLogger] 实例。
	 * @see getLogger
	 */
	@JvmName("__Kt_inline_reified_T_Any_getLogger")
	inline fun <reified T : Any> getLogger(): MLogger = getLogger(T::class.java)
	
	/**
	 * 解析调用本方法的外部类名。
	 *
	 * 通过 [Throwable.getStackTrace] 遍历当前线程栈，跳过 [Loggers] 自身的
	 * 栈帧，返回第一个外部帧的类名。实现可在 JDK 8 下运行，不依赖 JDK 9+
	 * 的 `StackWalker`。
	 *
	 * ### 限制
	 * - 当前只跳过 [Loggers] 自身，反射、日志框架、lambda/匿名类产生的帧
	 *   可能被误认为业务类。
	 * - 每次调用都会捕获并解析完整栈（微秒级），不应放在热路径。
	 *
	 * @return 调用者的完全限定类名。
	 * @throws IllegalStateException 栈中没有任何外部帧时抛出。
	 */
	private fun resolveCallerClassName(): String {
		val stack = Throwable().stackTrace
		for (frame in stack) {
			val name = frame.className
			if (name.equals(Loggers::class.java.name)) continue
			return name
		}
		throw IllegalStateException("Cannot resolve caller class")
	}
	
	/**
	 * 获取**调用本方法的那一层外部类**对应的 [MLogger]，无需显式传入类或名称。
	 *
	 * 内部通过 [resolveCallerClassName] 解析调用者类名，再转调 [getLogger]
	 * （[String] 重载）走统一的缓存逻辑；与 `getLogger(ThatClass::class)`
	 * 返回同一实例。
	 *
	 * ### 用法
	 * ```kotlin
	 * class MyService {
	 *     companion object {
	 *         // 类加载时调用一次，自动解析到 MyService 的 Logger
	 *         private val log = Loggers.getLogger()
	 *     }
	 * }
	 * ```
	 *
	 * ### 注意
	 * - 每次调用都会解析一次调用栈（微秒级），建议只在类级别初始化一次，
	 *   不要放入方法体或循环。
	 * - 在 lambda、匿名类或反射构造器中调用时，解析结果可能指向合成类名；
	 *   此时应改用 `getLogger(Class)` 或 `getLogger<T>()`。
	 *
	 * ### 与 reified 重载的关系
	 * 本函数与 `getLogger<T>()` 在 JVM 层签名冲突，后者的 JVM 名称已通过
	 * `@JvmName` 改为 `__Kt_inline_reified_T_Any_getLogger`。Kotlin 源码层
	 * 靠调用语法区分：`getLogger()` 走本函数，`getLogger<MyService>()` 走
	 * reified 版本。
	 *
	 * @return 调用者类对应的 [MLogger] 实例。
	 * @see resolveCallerClassName
	 * @see getLogger
	 */
	@JvmStatic
	fun getLogger() = getLogger(resolveCallerClassName())
}
