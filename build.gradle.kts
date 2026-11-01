import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

val kotlinStyle by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

apply(from = "gradle/architecture.gradle.kts")
dependencies { kotlinStyle("com.pinterest.ktlint:ktlint-cli:1.7.1:all") }

val kotlinSources = fileTree(rootDir) {
    include("**/src/**/*.kt", "**/*.gradle.kts")
    exclude("**/build/**", ".gradle/**", ".idea/**", "artifacts/**")
}
fun JavaExec.configureKotlinStyle(format: Boolean) {
    group = "verification"
    description = if (format) "Format Kotlin sources." else "Verify Kotlin formatting."
    classpath = kotlinStyle
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args("**/src/**/*.kt", "**/*.gradle.kts", "!**/build/**", "!**/.gradle/**", "!artifacts/**")
    if (format) args("--format")
}
tasks.register<JavaExec>("checkKotlinStyle") {
    configureKotlinStyle(format = false)
    inputs.files(kotlinSources, file(".editorconfig"))
}
tasks.register<JavaExec>("formatKotlin") { configureKotlinStyle(format = true) }

tasks.register("exportRuntimeDependencies") {
    group = "verification"
    description = "Export resolved local Android release Maven coordinates for vulnerability checks; no application data or secrets."
    val destination = layout.projectDirectory.file("artifacts/security/runtime-dependencies.tsv")
    outputs.file(destination)
    outputs.upToDateWhen { false }
    doLast {
        val scopes = listOf(
            Triple(":app", "releaseRuntimeClasspath", "android-release")
        )
        val rows = scopes.flatMap { (module, configuration, scope) ->
            project(module).configurations.getByName(configuration).incoming.resolutionResult.allComponents.mapNotNull {
                (it.id as? ModuleComponentIdentifier)?.let { id -> "$scope\t${id.group}:${id.module}\t${id.version}" }
            }
        }.distinct().sorted()
        require(rows.isNotEmpty())
        destination.asFile.parentFile.mkdirs()
        destination.asFile.writeText("scope\tpackage\tversion\n" + rows.joinToString("\n", postfix = "\n"))
    }
}
