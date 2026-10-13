/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins { alias(libs.plugins.android.library) }
android {
    namespace = "fr.sygix.tsuyu.ipc"
    compileSdk = 37
    defaultConfig { minSdk = 34 }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
