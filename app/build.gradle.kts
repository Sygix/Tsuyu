/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun versionCodeOf(version: String): Int {
    val match = requireNotNull(
        Regex("""(\d{1,2})\.(\d{1,3})\.(\d{1,3})(?:-(alpha|beta|rc)\.?(\d{1,2}))?""").matchEntire(version)
    ) { "TSUYU_VERSION doit être vX.Y.Z ou vX.Y.Z-(alpha|beta|rc).N, reçu : $version" }
    val (major, minor, patch, stage, n) = match.destructured
    require(major.toInt() <= 20) { "major doit être ≤ 20 (plafond du versionCode Android), reçu : $version" }
    val suffix = if (stage.isEmpty()) 99 else {
        require(n.toInt() <= 29) { "le numéro de pré-release doit être ≤ 29, reçu : $version" }
        mapOf("alpha" to 0, "beta" to 30, "rc" to 60).getValue(stage) + n.toInt()
    }
    return major.toInt() * 100_000_000 + minor.toInt() * 100_000 + patch.toInt() * 100 + suffix
}

android {
    namespace = "fr.sygix.tsuyu"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.sygix.tsuyu"
        minSdk = 34
        targetSdk = 37
        val ciVersion = System.getenv("TSUYU_VERSION")?.removePrefix("v")
        versionCode = ciVersion?.let(::versionCodeOf) ?: 1
        versionName = ciVersion ?: "0.0.1"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("TSUYU_STORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("TSUYU_STORE_PASSWORD")
                keyAlias = System.getenv("TSUYU_KEY_ALIAS")
                keyPassword = System.getenv("TSUYU_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":ipc"))
    implementation(project(":api"))
    implementation(libs.datastore.preferences)
    implementation(project(":server"))
    implementation(libs.tv.material)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.test.manifest)
}
