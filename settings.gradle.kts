plugins {
    id("com.gradleup.nmcp.settings") version("1.4.4")
}

rootProject.name = "logger-slf4j-api"

nmcpSettings {
    centralPortal {
        username = System.getenv("sonatype.username")
        password = System.getenv("sonatype.password")
        publishingType = "USER_MANAGED"
    }
}
