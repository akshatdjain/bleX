// Root build.gradle.kts — declares plugin versions for the whole project
plugins {
    // Android Gradle Plugin — DO NOT apply here, just declare the version
    id("com.android.application") version "8.7.3" apply false

    // Kotlin Android plugin
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false

    // Compose Compiler plugin (required since Kotlin 2.0)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
