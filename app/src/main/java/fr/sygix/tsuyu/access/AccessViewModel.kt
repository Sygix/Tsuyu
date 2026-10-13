/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.sygix.tsuyu.ipc.ITsuyuServer
import fr.sygix.tsuyu.start.BoundedIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccessApp(val name: String, val icon: Bitmap)
data class AccessRow(val uid: Int, val apps: List<AccessApp>)
data class AccessUi(val loading: Boolean = true, val rows: List<AccessRow> = emptyList(), val error: Boolean = false, val finished: Boolean = false, val focus: Int? = null)

class AccessViewModel(private val context: Context, private val server: () -> ITsuyuServer?, private val token: String?, private val io: BoundedIo = BoundedIo(), ended: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow()) : ViewModel() {
    private val mutable = MutableStateFlow(AccessUi())
    val ui = mutable.asStateFlow()
    init {
        viewModelScope.launch { ended.collect { if (it == token) mutable.value = mutable.value.copy(finished = true) } }
        refresh()
    }
    fun refresh() { viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, error = false)
        try {
            val rows = io.call(5000) {
                val service = server() ?: error("Start Tsuyu first")
                val bundle = if (token == null) service.access else service.getRequest(token)
                val uids = if (token == null) bundle.getIntArray("uids") ?: intArrayOf() else intArrayOf(bundle.getInt("uid"))
                uids.map { uid ->
                    val names = requireNotNull(bundle.getStringArrayList(if (token == null) uid.toString() else "packages"))
                    AccessRow(uid, names.map { name ->
                        val info = context.packageManager.getApplicationInfo(name, 0)
                        AccessApp(info.loadLabel(context.packageManager).toString(), info.loadIcon(context.packageManager).toBitmap(64, 64))
                    })
                }
            }
            if (!mutable.value.finished) mutable.value = AccessUi(loading = false, rows = rows, focus = fallback(mutable.value.focus, rows.map { it.uid }))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.e("Tsuyu", "Access lookup failed", failure); if (!mutable.value.finished) mutable.value = AccessUi(loading = false, error = true) }
    } }
    fun confirm(allowed: Boolean) { viewModelScope.launch {
        try { io.call(5000) { requireNotNull(server()).confirm(requireNotNull(token), allowed) }; mutable.value = mutable.value.copy(finished = true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.e("Tsuyu", "Confirmation failed", failure); mutable.value = mutable.value.copy(loading = false, error = true) }
    } }
    fun revoke(uid: Int) { viewModelScope.launch {
        val old = mutable.value.rows.map { it.uid }
        try {
            io.call(5000) { requireNotNull(server()).revoke(uid) }
            val rows = mutable.value.rows.filterNot { it.uid == uid }
            val index = old.indexOf(uid)
            mutable.value = mutable.value.copy(rows = rows, focus = rows.getOrNull(index)?.uid ?: rows.lastOrNull()?.uid)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.e("Tsuyu", "Revocation incomplete", failure); mutable.value = mutable.value.copy(error = true) }
    } }
    companion object { fun fallback(wanted: Int?, uids: List<Int>): Int? = wanted?.takeIf { it in uids } ?: uids.firstOrNull() }
}
