/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.sygix.tsuyu.TsuyuGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageReplacementReceiver(
    private val cleanup: suspend (Context) -> Unit = { TsuyuGraph.get(it).stopReplacedServer() },
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { cleanup(context) }
            catch (failure: Exception) { Log.e("Tsuyu", "Replacement cleanup failed", failure) }
            finally { pending.finish() }
        }
    }
}
