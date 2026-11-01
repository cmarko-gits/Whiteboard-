import org.gradle.api.artifacts.ProjectDependency

tasks.register("checkArchitecture") {
    group = "verification"
    description = "Enforce domain purity and presentation/platform dependency boundaries."
    doLast {
        val allowed = mapOf(
            ":core" to emptySet(),
            ":format" to setOf(":core"),
            ":canvas" to setOf(":core"),
            ":data" to setOf(":core", ":format"),
            ":core:ui" to setOf(":core"),
            ":platform" to setOf(":core", ":format", ":canvas"),
            ":feature:workspace" to setOf(":core", ":core:ui", ":canvas"),
            ":app" to
                setOf(":core", ":core:ui", ":format", ":data", ":canvas", ":platform", ":feature:workspace")
        )
        allowed.forEach { (path, targets) ->
            val module = project(path)
            listOf("api", "implementation", "compileOnly", "runtimeOnly").forEach { name ->
                module.configurations.findByName(name)?.dependencies?.filterIsInstance<ProjectDependency>()?.forEach {
                    check(it.path in targets) { "$path must not depend on ${it.path} through $name" }
                }
            }
            val forbidden = when {
                path == ":core" -> Regex(
                    "^import (android\\.|androidx\\.|dev\\.whiteboard\\.(data|format|canvas|app|ui)\\.)",
                    RegexOption.MULTILINE
                )
                path.startsWith(":feature:") || path == ":core:ui" -> Regex(
                    "^import (androidx\\.(room|datastore)\\.|dev\\.whiteboard\\.(data|format)\\.|dev\\.whiteboard\\.core\\.repository\\.|dev\\.whiteboard\\.app\\.workspace\\.(DocumentFiles|LocalMediaStore))",
                    RegexOption.MULTILINE
                )
                else -> null
            }
            module.fileTree("src/main").matching { include("**/*.kt") }.forEach { source ->
                val text = source.readText()
                check(forbidden?.containsMatchIn(text) != true) { "Forbidden layer import: ${source.relativeTo(rootDir)}" }
                check(!Regex("\\bGlobalScope\\b|\\brunBlocking\\s*[({]").containsMatchIn(text)) {
                    "Unstructured/blocking coroutine in production source: ${source.relativeTo(rootDir)}"
                }
            }
        }
    }
}
