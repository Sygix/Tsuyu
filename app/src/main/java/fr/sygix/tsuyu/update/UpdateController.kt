/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import android.util.Log
import fr.sygix.tsuyu.start.BoundedIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class UpdatePhase { IDLE, CHECKING, DOWNLOADING, WAITING, INSTALLING }

data class UpdateUi(
    val installed: String = "",
    val offer: Release? = null,
    val checkedAt: Long? = null,
    val result: String? = null,
    val auto: Boolean = false,
    val prereleases: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val loaded: Boolean = false,
)

interface UpdateHost {
    val installed: Long
    val installedVersion: String
    fun boot(): Int
    fun foreground(): Boolean
    suspend fun running(): Boolean
    suspend fun prepared(): Boolean
    suspend fun idle(): Boolean
    suspend fun install(file: File, release: Release): UpdateCause?
    fun schedule(auto: Boolean)
    fun recheck(delayMs: Long)
}

class UpdateController(
    private val storage: UpdateStorage,
    private val source: ReleaseSource,
    private val files: UpdateFiles,
    private val host: UpdateHost,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: BoundedIo = BoundedIo(),
) {
    private val mutable = MutableStateFlow(UpdateUi(installed = host.installedVersion))
    val state = mutable.asStateFlow()
    private val noticeFlow = MutableStateFlow<String?>(null)
    val notice = noticeFlow.asStateFlow()
    private val mutex = Mutex()

    suspend fun load() = mutex.withLock {
        val saved = storage.update { value ->
            val pending = value.pending?.takeIf { it.boot == host.boot() && File(it.path).exists() && clock() - it.verifiedAt in 0..WAIT_WINDOW_MS }
            value.copy(pending = pending)
        }
        files.clean(saved.pending?.let { File(it.path) })
        if (saved.target != null && host.installed >= saved.target && !saved.noticeShown) noticeFlow.value = saved.targetVersion
        publish(saved, UpdatePhase.IDLE)
    }

    suspend fun consumeNotice() {
        if (noticeFlow.value == null) return
        noticeFlow.value = null
        storage.update { it.copy(noticeShown = true, target = null, targetVersion = null) }
    }

    suspend fun check(manual: Boolean) = mutex.withLock { checkLocked(manual) }

    suspend fun setAuto(enabled: Boolean) = mutex.withLock {
        val saved = storage.update { it.copy(auto = enabled) }
        host.schedule(enabled)
        publish(saved, UpdatePhase.IDLE)
    }

    suspend fun setPrereleases(enabled: Boolean) = mutex.withLock {
        publish(storage.update { it.copy(prereleases = enabled) }, UpdatePhase.IDLE)
    }

    suspend fun update(): UpdateCause? = mutex.withLock {
        val offer = mutable.value.offer ?: return@withLock UpdateCause.WITHDRAWN
        val cause = preconditions() ?: run {
            val prepared = prepare(offer)
            if (prepared is Prepared.Failed) prepared.cause else install((prepared as Prepared.Ready).file, offer)
        }
        if (cause != null) record(cause)
        cause
    }

    suspend fun automatic() = mutex.withLock {
        var saved = storage.read()
        if (!saved.auto) return@withLock
        val pending = saved.pending
        if (pending != null) {
            val release = saved.releases.firstOrNull { it.code == pending.code }
            if (release != null) { waitOrInstall(release, File(pending.path), pending); return@withLock }
        }
        checkLocked(false)
        saved = storage.read()
        val offer = Releases.offer(saved.releases, host.installed, saved.prereleases) ?: return@withLock
        preconditions()?.let { record(it); return@withLock }
        when (val prepared = prepare(offer)) {
            is Prepared.Failed -> record(prepared.cause)
            is Prepared.Ready -> {
                val next = Pending(offer.code, offer.version, prepared.file.path, clock(), host.boot())
                storage.update { it.copy(pending = next) }
                waitOrInstall(offer, prepared.file, next)
            }
        }
    }

    private suspend fun waitOrInstall(release: Release, file: File, pending: Pending) {
        if (clock() - pending.verifiedAt !in 0..WAIT_WINDOW_MS || !file.exists()) {
            drop(file)
            return
        }
        val idle = !host.foreground() && try { host.idle() } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
            false
        } catch (failure: Exception) { false }
        if (!idle) {
            mutable.value = mutable.value.copy(phase = UpdatePhase.WAITING)
            host.recheck(IDLE_RECHECK_MS)
            return
        }
        val cause = preconditions() ?: install(file, release)
        if (cause != null) { drop(file); record(cause) }
    }

    private suspend fun drop(file: File) {
        file.delete()
        publish(storage.update { it.copy(pending = null) }, UpdatePhase.IDLE)
    }

    private suspend fun preconditions(): UpdateCause? = when {
        !host.running() -> UpdateCause.SERVICE
        !host.prepared() -> UpdateCause.PREPARATION
        else -> null
    }

    private sealed interface Prepared {
        data class Ready(val file: File) : Prepared
        data class Failed(val cause: UpdateCause) : Prepared
    }

    private suspend fun prepare(offer: Release): Prepared {
        mutable.value = mutable.value.copy(phase = UpdatePhase.DOWNLOADING)
        val fresh = fetch()
        if (fresh is Fetch.Found) {
            val saved = storage.update { it.copy(releases = fresh.releases) }
            if (fresh.releases.none { it == offer } || Releases.offer(fresh.releases, host.installed, saved.prereleases) == null) {
                publish(saved, UpdatePhase.IDLE)
                return Prepared.Failed(UpdateCause.WITHDRAWN)
            }
        } else return Prepared.Failed(if (fresh is Fetch.Failed) fresh.cause else UpdateCause.LIMITED)
        val file = files.file(offer)
        val cause = try {
            io.call(DOWNLOAD_TIMEOUT_MS) { files.download(offer, file) ?: files.verify(offer, file, host.installed) }
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
            UpdateCause.TIMEOUT
        } catch (failure: Exception) {
            Log.e("Tsuyu", "Update download failed", failure)
            UpdateCause.NETWORK
        }
        if (cause != null) { file.delete(); return Prepared.Failed(cause) }
        return Prepared.Ready(file)
    }

    private suspend fun install(file: File, release: Release): UpdateCause? {
        mutable.value = mutable.value.copy(phase = UpdatePhase.INSTALLING)
        storage.update { it.copy(target = release.code, targetVersion = release.version, noticeShown = false) }
        val cause = try { host.install(file, release) } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
            UpdateCause.TIMEOUT
        } catch (failure: Exception) {
            Log.e("Tsuyu", "Update installation failed", failure)
            UpdateCause.INSTALLER
        }
        file.delete()
        if (cause != null) storage.update { it.copy(target = null, targetVersion = null, noticeShown = true, pending = null) }
        return cause
    }

    private suspend fun checkLocked(manual: Boolean) {
        val saved = storage.read()
        val now = clock()
        if (!manual && !due(saved, now)) { publish(saved, UpdatePhase.IDLE); return }
        mutable.value = mutable.value.copy(phase = UpdatePhase.CHECKING)
        val next = when (val fetched = fetch()) {
            is Fetch.Found -> storage.update {
                val offer = Releases.offer(fetched.releases, host.installed, it.prereleases)
                it.copy(releases = fetched.releases, checkedAt = now, result = if (offer == null) UP_TO_DATE else AVAILABLE)
            }
            is Fetch.Limited -> storage.update { it.copy(checkedAt = now, retryAt = fetched.retryAt, result = UpdateCause.LIMITED.name) }
            is Fetch.Failed -> storage.update { it.copy(checkedAt = now, result = fetched.cause.name) }
        }
        publish(next, UpdatePhase.IDLE)
    }

    private suspend fun fetch(): Fetch = try { io.call(FETCH_TIMEOUT_MS) { source.fetch() } }
    catch (cancelled: CancellationException) {
        if (cancelled !is TimeoutCancellationException) throw cancelled
        Fetch.Failed(UpdateCause.TIMEOUT)
    } catch (failure: Exception) {
        Log.e("Tsuyu", "Release check failed", failure)
        Fetch.Failed(if (failure is fr.sygix.tsuyu.start.StartException) UpdateCause.BUSY else UpdateCause.NETWORK)
    }

    private suspend fun record(cause: UpdateCause) = publish(storage.update { it.copy(result = cause.name) }, UpdatePhase.IDLE)

    private fun publish(saved: UpdateSaved, phase: UpdatePhase) {
        mutable.value = UpdateUi(
            installed = host.installedVersion,
            offer = Releases.offer(saved.releases, host.installed, saved.prereleases),
            checkedAt = saved.checkedAt,
            result = saved.result,
            auto = saved.auto,
            prereleases = saved.prereleases,
            phase = if (phase == UpdatePhase.IDLE && saved.pending != null) UpdatePhase.WAITING else phase,
            loaded = true,
        )
    }

    companion object {
        const val UP_TO_DATE = "UP_TO_DATE"
        const val AVAILABLE = "AVAILABLE"
        const val DAY_MS = 86_400_000L
        const val WAIT_WINDOW_MS = DAY_MS
        const val IDLE_RECHECK_MS = 900_000L
        private const val FETCH_TIMEOUT_MS = 25_000L
        private const val DOWNLOAD_TIMEOUT_MS = 600_000L

        fun due(saved: UpdateSaved, now: Long): Boolean {
            val checked = saved.checkedAt
            return (checked == null || checked > now || now - checked >= DAY_MS) && now >= saved.retryAt
        }
    }
}
