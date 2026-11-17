import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    `maven-publish`
    signing
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

group = "com.mingliqiye.logger"
version = "1.0.2-S2.0.20"

val buildDir: java.nio.file.Path = File("build").toPath()

repositories {
    maven {
        name = "mingliqiye-public"
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.20")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.1")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

kotlin {
    jvmToolchain(8)
}

tasks.withType<org.gradle.jvm.tasks.Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("LICENSE") { into("META-INF") }
    from("NOTICE") { into("META-INF") }
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to rootProject.name,
                "Specification-Version" to rootProject.version,
                "Specification-Vendor" to "minglipro",
                "Specification-Build-Time" to LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS")),
                "Specification-Package" to rootProject.group,
                "Specification-Build-Number" to "1",
                "Specification-Build-OS" to System.getProperty("os.name"),
                "Specification-Build-Java" to System.getProperty("java.version"),
                "Specification-Build-Java-Vendor" to System.getProperty("java.vendor"),
                "Specification-Build-Java-Vendor-URL" to System.getProperty("java.vendor.url"),
                "Implementation-Title" to rootProject.name,
                "Implementation-Version" to rootProject.version,
                "Implementation-Package" to rootProject.group,
                "Implementation-Vendor" to "minglipro",
                "Implementation-Build-Time" to LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS"))

            )
        )
    }
}

tasks.register<Jar>("javaDocJar") {
    group = "build"
    archiveClassifier.set("javadoc")
    dependsOn("dokkaGeneratePublicationJavadoc")
    from(buildDir.resolve("dokka/javadoc"))
}
tasks.register<Jar>("kotlinDocJar") {
    group = "build"
    archiveClassifier.set("kotlindoc")
    dependsOn("dokkaGeneratePublicationHtml")
    from(buildDir.resolve("dokka/html"))
}

tasks.build {
    dependsOn("javaDocJar", "kotlinDocJar")
}


publishing {
    repositories {
        maven {
            name = "MavenRepositoryRaw"
            url = uri("C:/data/git/maven-repository-raw")
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks.named("javaDocJar"))
            artifact(tasks.named("kotlinDocJar"))
            artifactId = rootProject.name
            pom {
                name = "logger-slf4j-api"
                url = "https://github.com/minglipro/logger-slf4j-api"
                description = "A Java/kotlin Slf4j Logger"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "minglipro"
                        name = "mingli"
                        email = "minglipro@163.com"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/minglipro/logger-slf4j-api.git"
                    developerConnection = "scm:git:https://git.mingliqiye.com:minglipro/logger-slf4j-api.git"
                    url = "https://github.com/minglipro/logger-slf4j-api"
                }
            }
        }
    }
    signing {
        sign(
            publishing.publications
        )
    }
}

tasks.named("generateMetadataFileForMavenJavaPublication") {
    dependsOn("javaDocJar")
    dependsOn("kotlinDocJar")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    outputs.upToDateWhen { false }
    filesMatching(listOf("fabric.mod.json")) {
        expand(
            mapOf(
                "VERSIONS" to project.version,
                "GROUPSID" to project.group,
                "ARTIFACTID" to project.name,
                "JDKVERSIONS" to 8,
                "buildTime" to LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd HH:mm:ss.SSSSSSS"
                    )
                )
            )
        )
    }
}

signing {
    val secretKeyRingFile = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    val keyId = System.getenv("SIGNING_KEY_ID")
    useGpgCmd()
    project.ext.set("signing.secretKeyRingFile", secretKeyRingFile)
    project.ext.set("signing.keyId", keyId)
    project.ext.set("signing.password", "")
}
