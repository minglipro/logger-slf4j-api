package com.mingliqiye.logger.ibatis

import com.mingliqiye.logger.*
import org.apache.ibatis.logging.Log
import org.slf4j.Logger
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger


class LoggerSlf4jImpl : Log {
    private val fqdn = "com.mingliqiye.logger.ibatis.LoggerSlf4jImpl"
    private val log: Logger
    private val llog: LocationAwareLogger?

    constructor(clazz: String) {
        log = getLogger(clazz)
        llog = log as? LocationAwareLogger
    }

    constructor(clazz: Class<*>) {
        log = getLogger(clazz)
        llog = log as? LocationAwareLogger
    }

    fun log(level: Level, msg: String?, throwable: Throwable? = null) {
        val msgs = msg ?: "null"
        if (llog == null) {
            log.atLevel(level).setCause(throwable).log(msgs)
        } else {
            llog.log(null, fqdn, level.toInt(), msgs, null, throwable)
        }
    }

    override fun isDebugEnabled(): Boolean = log.isDebugEnabled
    override fun isTraceEnabled(): Boolean = log.isTraceEnabled

    override fun error(s: String?, e: Throwable?) = log(Level.ERROR, s, e)

    override fun error(s: String?) = log(Level.ERROR, s)

    override fun debug(s: String?) = log(Level.DEBUG, s)

    override fun trace(s: String?) = log(Level.TRACE, s)

    override fun warn(s: String?) = log(Level.WARN, s)
}

private val log = getLogger("666")

fun main() {
    log info {
        "ooo"
    }
}
