plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.blegod.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blegod.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = "3.0.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file("blegod-release.jks")
            storePassword = "blegod123"
            keyAlias = "blegod"
            keyPassword = "blegod123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // ── Jetpack Compose (BOM manages all Compose versions) ────────
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── MQTT (Eclipse Paho) ────────────────────────────────────────
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // ── JSON ───────────────────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.11.0")

    // ── Coroutines ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Embedded MQTT Broker (Moquette) ──────────────────────────
    implementation("io.moquette:moquette-broker:0.17") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
        exclude(group = "log4j", module = "log4j")
    }
    implementation("org.slf4j:slf4j-nop:1.7.36")
}
