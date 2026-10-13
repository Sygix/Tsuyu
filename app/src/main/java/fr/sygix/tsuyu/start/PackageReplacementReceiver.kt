/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.sygix.tsuyu.TsuyuGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageReplacementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { TsuyuGraph.get(context).stopReplacedServer() }
            catch (failure: Exception) { android.util.Log.e("Tsuyu", "Replacement cleanup failed", failure) }
            finally { pending.finish() }
        }
    }
}
