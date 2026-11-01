plugins { alias(libs.plugins.kotlin.jvm) }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core"))
    // Android supplies org.json; the JVM server declares its own runtime implementation.
    compileOnly(libs.json)
    testImplementation(libs.json)
    testImplementation(libs.junit)
}
