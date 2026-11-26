package com.mingliqiye.logger

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import java.io.IOException


class LoggersTest{

     private val log = Loggers.getLogger()

    @Test
    fun testLogger(){
        val lofd = "sadwsad"
        log info lofd
        Thread.sleep(1000)
    }

    @Test
    fun testMemberInfixAndOverloads() {
        val any: Any = 1
        log.info(any)
        log.info("text")
        log.info(IOException("boom"))
        log info "infix"
        log info null
        log debug "debug"
        log warn "warn"
        log error "error"
        log trace "trace"
        log info IOException("infix exception")
        log.debug("normal", any)
    }

    @Test
    fun testCacheReturnsSameInstanceForNameAndClass() {
        val byClass = Loggers.getLogger(Loggers::class.java)
        val byName = Loggers.getLogger(Loggers::class.java.name)
        val reified = Loggers.getLogger<Loggers>()
        assertSame(byClass, byName)
        assertSame(byClass, reified)
    }
}