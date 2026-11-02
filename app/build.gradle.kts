plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
val qualificationTests = providers.gradleProperty("whiteboard.qualificationTests").orNull == "true"
val leakDetection = providers.gradleProperty("whiteboard.leakDetection").orNull?.toBooleanStrictOrNull() != false
check(!qualificationTests || leakDetection) { "Qualification tests require -Pwhiteboard.leakDetection=true" }
android {
    namespace = "dev.whiteboard.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.whiteboard.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
    if (qualificationTests) sourceSets.getByName("androidTest").kotlin.srcDir("src/qualification/kotlin")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:workspace"))
    implementation(project(":platform"))
    implementation(project(":core"))
    implementation(project(":format"))
    implementation(project(":data"))
    implementation(project(":canvas"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.graphics.path)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.exif)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
    androidTestImplementation(libs.compose.test.junit)
    // Compose's older transitive Espresso uses an InputManager API removed on recent Android versions.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(testFixtures(project(":core")))
    debugImplementation(libs.compose.test.manifest)
    if (leakDetection) {
        debugImplementation(libs.leakcanary)
    }
    if (qualificationTests) {
        androidTestImplementation(libs.leakcanary.instrumentation)
        androidTestImplementation(libs.shark.graph)
    }
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.ui.automator)
}
