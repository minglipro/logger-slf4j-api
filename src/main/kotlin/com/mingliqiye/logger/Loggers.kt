package com.mingliqiye.logger

import com.mingliqiye.logger.Loggers.getLogger
import com.mingliqiye.logger.Loggers.loggerCache
import com.mingliqiye.logger.Loggers.resolveCallerClassName
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 日志器（[MLogger]）的全局工厂与缓存入口。
 *
 * 该对象负责按名称或类型创建 [MLogger] 实例，并通过 [ConcurrentHashMap] 缓存，
 * 保证**同一名称在进程内只对应一个 [MLogger] 实例**。所有 `getLogger` 重载
 * 最终都汇聚到按名称缓存的两个私有方法上，因此 `getLogger("a.B")` 与
 * `getLogger(A::class)`（当 `A` 的完全限定名为 `a.B` 时）会返回同一个实例。
 *
 * ### 线程安全
 * 缓存读写全部通过 `ConcurrentHashMap.computeIfAbsent` 完成，该操作对同一 key
 * 是原子的：并发调用下映射函数最多执行一次，其余线程会等待并拿到同一结果。
 * 因此本对象的所有公开方法均可安全地被多线程同时调用。
 *
 * ### 典型用法
 * ```kotlin
 * // 按类获取（推荐，Logger 名即类的完全限定名）
 * val logger = Loggers.getLogger(MyService::class)
 *
 * // 按名称获取
 * val named = Loggers.getLogger("com.example.custom")
 *
 * // reified 便捷写法，无需显式传 Class
 * val reified = Loggers.getLogger<MyService>()
 *
 * // 零参数写法：自动解析调用者类（见 [getLogger] 无参重载）
 * class MyService {
 *     private val log = Loggers.getLogger()
 * }
 * ```
 *
 * @see MLogger
 */
object Loggers {
	
	/**
	 * 按 Logger 名称缓存 [MLogger] 实例的并发映射。
	 *
	 * - key 为 Logger 名称（通常是类的完全限定名，或调用方自定义的字符串）。
	 * - value 为对应的 [MLogger] 包装实例。
	 * - 初始容量 25、负载因子 0.8 仅为减少扩容次数的经验值；实际容量会被
	 *   [ConcurrentHashMap] 向上取整到 2 的幂。条目数量不多时也可直接使用默认构造。
	 *
	 * 该映射的读写**只能**通过 `computeIfAbsent` 进行，以保证并发下同一名称
	 * 只创建一个实例。不要在外部直接 `put` 或 `get` 后手动判断。
	 */
	private val loggerCache = ConcurrentHashMap<String, MLogger>(25, 0.8f)
	
	/**
	 * 按名称获取或创建 [MLogger]，并写入缓存。
	 *
	 * 使用 `ConcurrentHashMap.computeIfAbsent` 保证并发下同一名称只创建一次实例。
	 * 若缓存中已存在该名称的实例，直接返回；否则调用 [MLogger] 的字符串构造器创建并缓存。
	 *
	 * @param name Logger 名称，通常为类的完全限定名或自定义标识；不能为 `null`。
	 * @return 与 [name] 对应的、进程内唯一的 [MLogger] 实例。
	 */
	private fun getLoggerForCache(name: String): MLogger =
		loggerCache.computeIfAbsent(name) { MLogger(it) }
	
	/**
	 * 按类型获取或创建 [MLogger]，并写入缓存。
	 *
	 * 缓存 key 为 [clazz] 的完全限定名（[Class.getName]），因此
	 * `getLogger(SomeClass::class.java)` 与 `getLogger(SomeClass::class.java.name)`
	 * 会命中同一缓存条目。映射函数使用 [Class] 构造器创建 [MLogger]，
	 * 以便包装类保留类型信息。
	 *
	 * @param clazz 用于推断 Logger 名称的类；不能为 `null`。
	 * @return 与 [clazz] 对应的、进程内唯一的 [MLogger] 实例。
	 */
	private fun getLoggerForCache(clazz: Class<*>): MLogger =
		loggerCache.computeIfAbsent(clazz.name) { MLogger(clazz) }
	
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
	 * 通过 [Throwable.getStackTrace] 获取当前线程的调用栈，从栈顶向下逐帧扫描，
	 * 跳过类名等于 [Loggers] 自身的帧，返回第一个外部帧的类名。等价于 JDK 9+
	 * 的 `StackWalker.getCallerClass().getName()`，但不依赖 `StackWalker`，
	 * 因此可在 JDK 8 下运行。
	 *
	 * ### 当前实现的局限
	 * - **只跳过了 [Loggers] 自身**。若调用链上还有其他包装类（如 [MLogger]、
	 *   或第三方日志框架的入口），或被反射（`java.lang.reflect.` /
	 *   `sun.reflect.`）与日志框架（`org.slf4j.` / `ch.qos.logback.`）帧插入，
	 *   返回的类名可能不是真正的业务类。生产环境建议把这些前缀一并加入跳过名单。
	 * - **包含一行 `println` 调试输出**，会在每次解析时打印每个栈帧的类名。
	 *   生产代码中应移除，否则既污染标准输出又有性能开销。
	 *
	 * ### 性能
	 * 每次调用都会创建 [Throwable] 并解析完整栈（微秒级），无法通过 [loggerCache]
	 * 避免——必须先知道类名才能查缓存。调用方应在 `companion object` 中用
	 * `private val` 只调用一次，切勿放进热路径。
	 *
	 * @return 调用者的完全限定类名。
	 * @throws IllegalStateException 若扫描完整个栈仍未找到非 [Loggers] 的帧。
	 */
	private fun resolveCallerClassName(): String {
		val stack = Throwable().stackTrace
		for (frame in stack) {
			val name = frame.className
			println(name)
			if (name.equals(Loggers::class.java.name)) continue
			return name
		}
		throw IllegalStateException("Cannot resolve caller class")
	}
	
	/**
	 * 获取**调用本方法的那一层外部类**对应的 [MLogger]，无需显式传入类或名称。
	 *
	 * 内部调用 [resolveCallerClassName] 解析调用者类名，再转调
	 * [getLogger]（[String] 重载）走统一的缓存逻辑。若调用者类名相同，
	 * 返回的实例与 `getLogger(ThatClass::class)` 完全一致。
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
	 * ### 性能与陷阱
	 * 每次调用都要解析调用栈（微秒级），因此**必须在类级别只调用一次**，
	 * 不要写进方法体或循环。若在 lambda、匿名类、反射构造器内调用，
	 * 解析结果可能指向合成类或 JDK 内部类，需配合 [resolveCallerClassName]
	 * 的跳过策略一起排查。
	 *
	 * ### 与 reified 重载的关系
	 * 本函数与 `getLogger<T>()` 在 JVM 层签名冲突，已通过给后者加 `@JvmName`
	 * 解决。Kotlin 源码层靠调用语法区分：`getLogger()` 走本函数，
	 * `getLogger<MyService>()` 走 reified 版本。
	 *
	 * @return 调用者类对应的 [MLogger] 实例。
	 * @see resolveCallerClassName
	 * @see getLogger
	 */
	@JvmStatic
	fun getLogger() = getLogger(resolveCallerClassName())
}
