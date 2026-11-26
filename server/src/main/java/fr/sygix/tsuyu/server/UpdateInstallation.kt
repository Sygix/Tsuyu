/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.io.InputStream

enum class InstallStatus { SUCCESS, DIGEST, INSTALLER, TIMEOUT, UNSUPPORTED }
data class InstallOutcome(val status: InstallStatus, val detail: String = "")

fun interface UpdateInstallation {
    fun install(apk: InputStream, size: Long, sha256: String): InstallOutcome
}
