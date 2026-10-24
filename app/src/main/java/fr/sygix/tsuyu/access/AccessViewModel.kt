/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.content.Context
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.sygix.tsuyu.ipc.ITsuyuServer
import fr.sygix.tsuyu.start.BoundedIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccessApp(val name: String, val icon: Bitmap)
data class AccessRow(val uid: Int, val apps: List<AccessApp>, val cleanupPending: Boolean = false, val refused: Boolean = false)
data class AccessUi(
    val loading: Boolean = true,
    val rows: List<AccessRow> = emptyList(),
    val error: Boolean = false,
    val finished: Boolean = false,
    val focus: Int? = null,
    val permission: Boolean = false,
    val retryRevoke: Int? = null,
    val deliveryFailures: List<AccessApp> = emptyList(),
    val resetFailed: Int? = null,
) {
    val presentation = AccessPresentation.from(this)
}

class AccessViewModel(
    private val context: Context,
    private val server: () -> ITsuyuServer?,
    private val token: String?,
    private val io: BoundedIo = BoundedIo(),
    ended: Flow<String> = emptyFlow(),
) : ViewModel() {
    private val mutable = MutableStateFlow(AccessUi(permission = token != null))
    private var revocation: Job? = null
    val ui = mutable.asStateFlow()
    fun back() {
        if (token == null || mutable.value.error) mutable.value = mutable.value.copy(finished = true)
        else confirm(false)
    }
    init {
        viewModelScope.launch {
            ended.collect { if (it == token) mutable.value = mutable.value.copy(finished = true) }
        }
        refresh()
    }
    fun refresh() {
        viewModelScope.launch {
            mutable.value.retryRevoke?.let { revoke(it); return@launch }
            mutable.value = mutable.value.copy(loading = true, error = false, resetFailed = null)
            try {
                val (rows, failures) = io.call(LOOKUP_TIMEOUT_MS) { load() }
                if (!mutable.value.finished) {
                    mutable.value = mutable.value.copy(
                        loading = false, error = false, rows = rows, deliveryFailures = failures,
                        focus = fallback(mutable.value.focus, rows.map { it.uid }),
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.e("Tsuyu", "Access lookup failed", failure)
                if (!mutable.value.finished) mutable.value = mutable.value.copy(loading = false, error = true)
            }
        }
    }

    private fun load(): Pair<List<AccessRow>, List<AccessApp>> {
        val service = server() ?: error("Start Tsuyu first")
        val bundle = if (token == null) service.access else service.getRequest(token)
        val uids = if (token == null) bundle.getIntArray("uids") ?: intArrayOf() else intArrayOf(bundle.getInt("uid"))
        val pending = bundle.getIntArray("cleanupPending") ?: intArrayOf()
        val refused = bundle.getIntArray("refused") ?: intArrayOf()
        val rows = uids.map { uid ->
            val key = if (token == null) uid.toString() else "packages"
            val names = requireNotNull(bundle.getStringArrayList(key))
            AccessRow(uid, names.map(::app), uid in pending, uid in refused && uid !in pending)
        }.sortedWith(compareBy<AccessRow> { it.refused }.thenBy { row -> row.apps.joinToString { it.name }.lowercase() })
        return rows to bundle.getStringArrayList("deliveryFailures").orEmpty().map(::app)
    }

    private fun app(name: String): AccessApp {
        val manager = context.packageManager
        return try {
            val info = manager.getApplicationInfo(name, 0)
            AccessApp(info.loadLabel(manager).toString(), info.loadIcon(manager).toBitmap(64, 64))
        } catch (missing: PackageManager.NameNotFoundException) {
            AccessApp(name, manager.defaultActivityIcon.toBitmap(64, 64))
        }
    }

    fun confirm(allowed: Boolean) {
        viewModelScope.launch {
            try {
                io.call(CONFIRM_TIMEOUT_MS) { requireNotNull(server()).confirm(requireNotNull(token), allowed) }
                mutable.value = mutable.value.copy(finished = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.e("Tsuyu", "Confirmation failed", failure)
                mutable.value = mutable.value.copy(loading = false, error = true)
            }
        }
    }

    fun revoke(uid: Int) {
        if (revocation?.isActive == true) return
        revocation = viewModelScope.launch {
            val old = mutable.value.rows.map { it.uid }
            mutable.value = mutable.value.copy(loading = true, error = false, retryRevoke = uid, resetFailed = null)
            try {
                io.call(REVOKE_TIMEOUT_MS) { requireNotNull(server()).revoke(uid) }
                val rows = mutable.value.rows.filterNot { it.uid == uid }
                val index = old.indexOf(uid)
                mutable.value = mutable.value.copy(
                    loading = false, error = false, retryRevoke = null, rows = rows,
                    focus = rows.getOrNull(index)?.uid ?: rows.lastOrNull()?.uid,
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.e("Tsuyu", "Revocation incomplete", failure)
                mutable.value = mutable.value.copy(loading = false, error = true)
            }
        }
    }

    fun reset(uid: Int) {
        if (revocation?.isActive == true) return
        revocation = viewModelScope.launch {
            val old = mutable.value.rows.map { it.uid }
            mutable.value = mutable.value.copy(loading = true, error = false, resetFailed = null)
            try {
                if (!io.call(RESET_TIMEOUT_MS) { requireNotNull(server()).resetRefusal(uid) }) {
                    mutable.value = mutable.value.copy(loading = false)
                    revocation = null
                    refresh()
                    return@launch
                }
                val rows = mutable.value.rows.filterNot { it.uid == uid }
                val index = old.indexOf(uid)
                mutable.value = mutable.value.copy(loading = false, rows = rows, focus = rows.getOrNull(index)?.uid ?: rows.lastOrNull()?.uid)
            } catch (cancelled: CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                mutable.value = mutable.value.copy(loading = false, resetFailed = uid, focus = uid)
            } catch (failure: Exception) {
                Log.e("Tsuyu", "Refusal reset failed", failure)
                mutable.value = mutable.value.copy(loading = false, resetFailed = uid, focus = uid)
            }
        }
    }

    companion object {
        private const val RESET_TIMEOUT_MS = 10_000L
        private const val LOOKUP_TIMEOUT_MS = 10_000L
        private const val CONFIRM_TIMEOUT_MS = 5_000L
        private const val REVOKE_TIMEOUT_MS = 60_000L
        fun fallback(wanted: Int?, uids: List<Int>): Int? = wanted?.takeIf { it in uids } ?: uids.firstOrNull()
    }
}
