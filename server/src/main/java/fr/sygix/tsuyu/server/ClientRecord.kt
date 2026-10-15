/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import moe.shizuku.server.IShizukuApplication

internal data class ClientRecord(
    val uid: Int,
    val pid: Int,
    val group: AccessGroup,
    val api: Int,
    val callback: IShizukuApplication,
    val packageName: String,
)
