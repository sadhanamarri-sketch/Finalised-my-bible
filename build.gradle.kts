buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // KSP version format is <kotlin-version>-<ksp-version>. If Gradle can't
    // resolve this exact one, check https://github.com/google/ksp/releases
    // for the latest KSP build published against Kotlin 2.0.21 and swap it in.
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
