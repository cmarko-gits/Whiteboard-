plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
application {
    mainClass.set("dev.whiteboard.server.MainKt")
    applicationDefaultJvmArgs = listOf("-Xms128m", "-Xmx512m")
}
dependencies {
    implementation(project(":core"))
    implementation(project(":format"))
    implementation(libs.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status)
    implementation(libs.hikari)
    implementation(libs.bouncycastle)
    implementation(libs.mail)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test)
    testImplementation("io.zonky.test:embedded-postgres:2.2.2") { exclude(group = "io.zonky.test.postgres") }
    val platform = when {
        System.getProperty("os.name").startsWith("Windows", true) -> "windows"
        System.getProperty("os.name").startsWith("Mac", true) -> "darwin"
        else -> "linux"
    }
    val architecture = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64v8" else "amd64"
    testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-$platform-$architecture:18.6.0")
}
tasks.test { maxHeapSize = "768m" }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.register<Test>("postgresTest") {
    group = "verification"
    description = "Run the backend suite against an isolated loopback PostgreSQL process."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("whiteboard.test.postgres", "true")
    maxHeapSize = "768m"
    shouldRunAfter(tasks.test)
}
