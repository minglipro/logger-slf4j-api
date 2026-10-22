package org.apache.ibatis.logging

interface Log {
    fun isDebugEnabled(): Boolean
    fun isTraceEnabled(): Boolean
    fun error(s: String?, e: Throwable?)
    fun error(s: String?)
    fun debug(s: String?)
    fun trace(s: String?)
    fun warn(s: String?)
}
