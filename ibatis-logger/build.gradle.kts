import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.0.0"
}

group = "com.mingliqiye.logger.ibatis"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}
