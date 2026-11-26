/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.distribution

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Distribution {
    val offersUpdates: Boolean
    val notice: StateFlow<String?>
    val updateBlocked: Flow<Boolean>
    fun homeCreated()
    fun homeStopped()
    fun openUpdates(activity: Activity)
}
