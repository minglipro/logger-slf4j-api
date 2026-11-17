package com.mingliqiye.logger

import com.mingliqiye.logger.MLogger.Companion.info
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
}
