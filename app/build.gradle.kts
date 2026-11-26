/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun versionCodeOf(version: String): Int {
    val match = requireNotNull(
        Regex("""(\d{1,2})\.(\d{1,3})\.(\d{1,3})(?:-(alpha|beta|rc)\.?(\d{1,2}))?""").matchEntire(version)
    ) { "versionName must be X.Y.Z or X.Y.Z-(alpha|beta|rc).N in version.properties, got: $version" }
    val (major, minor, patch, stage, n) = match.destructured
    require(major.toInt() <= 20) { "major must be at most 20 (Android versionCode ceiling), got: $version" }
    val suffix = if (stage.isEmpty()) 99 else {
        require(n.toInt() <= 29) { "prerelease number must be at most 29, got: $version" }
        mapOf("alpha" to 0, "beta" to 30, "rc" to 60).getValue(stage) + n.toInt()
    }
    return major.toInt() * 100_000_000 + minor.toInt() * 100_000 + patch.toInt() * 100 + suffix
}

val versionFile = rootProject.file("version.properties")
val versionProperties = Properties().apply { versionFile.inputStream().use { load(it) } }
val releaseName: String = requireNotNull(versionProperties.getProperty("versionName")?.trim()?.takeIf { it.isNotEmpty() }) { "versionName missing in ${versionFile.name}" }
val releaseCode: Int = requireNotNull(versionProperties.getProperty("versionCode")?.trim()?.toIntOrNull()) { "versionCode missing or malformed in ${versionFile.name}" }
require(releaseCode == versionCodeOf(releaseName)) { "versionCode $releaseCode in ${versionFile.name} does not match versionName $releaseName (expected ${versionCodeOf(releaseName)})" }

base.archivesName = "tsuyu-$releaseName"

android {
    namespace = "fr.sygix.tsuyu"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.sygix.tsuyu"
        minSdk = 28
        targetSdk = 37
        versionCode = releaseCode
        versionName = releaseName
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("standalone") { dimension = "distribution" }
        create("store") { dimension = "distribution" }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    val storePath = System.getenv("TSUYU_STORE_FILE")
    signingConfigs {
        if (storePath != null) create("release") {
            storeFile = file(storePath)
            storePassword = System.getenv("TSUYU_STORE_PASSWORD")
            keyAlias = System.getenv("TSUYU_KEY_ALIAS")
            keyPassword = System.getenv("TSUYU_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (storePath != null) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "de", "es", "fr", "it")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":ipc"))
    implementation(project(":api"))
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    "standaloneImplementation"(libs.zxing.core)
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
