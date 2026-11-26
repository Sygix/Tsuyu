/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import androidx.lifecycle.ViewModelStore
import fr.sygix.tsuyu.start.BoundedIo
import fr.sygix.tsuyu.ui.Action
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateViewModelTest {
    private val release = Release("v0.2.0", "0.2.0", 200099, false, "https://example.org/tsuyu.apk", 4, "b".repeat(64), "https://example.org/v0.2.0")
    private val storage = object : UpdateStorage {
        var saved = UpdateSaved(releases = listOf(release), result = UpdateController.AVAILABLE, checkedAt = 1)
        override suspend fun read() = saved
        override suspend fun update(transform: (UpdateSaved) -> UpdateSaved) = transform(saved).also { saved = it }
    }
    private val directory = kotlin.io.path.createTempDirectory().toFile()
    private val files = object : UpdateFiles {
        override fun file(release: Release) = File(directory, "u.apk")
        override fun download(release: Release, target: File): UpdateCause? { target.writeBytes(ByteArray(4)); return null }
        override fun verify(release: Release, file: File, installed: Long): UpdateCause? = null
        override fun clean(keep: File?) = Unit
    }
    private var ready = false
    private val installs = mutableListOf<Release>()
    private val host = object : UpdateHost {
        override val installed = 100099L
        override val installedVersion = "0.1.0"
        override fun boot() = 1
        override fun foreground() = true
        override fun online() = true
        override suspend fun running() = true
        override suspend fun prepared() = ready
        override suspend fun idle() = true
        override suspend fun install(file: File, release: Release): UpdateCause? { installs += release; return null }
        override fun schedule(auto: Boolean) = Unit
        override fun recheck(delayMs: Long) = Unit
    }
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }

    @Test fun unpreparedUpdateOpensPreparationAndContinuesOnlyAfterAReadyOutcome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val work = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            val updates = UpdateController(storage, { Fetch.Found(listOf(release)) }, files, host, { 10L }, direct)
            updates.load()
            val model = UpdateViewModel(updates, MutableStateFlow(true), { host.prepared() }, work)
            store.put("updates", model)
            val opened = async { model.preparation.first() }
            model.act(Action.UPDATE)
            advanceUntilIdle()
            opened.await()
            assertTrue(installs.isEmpty())
            model.resumed()
            advanceUntilIdle()
            assertTrue(installs.isEmpty())
            assertEquals("update-install", model.ui.value.focus)
            model.act(Action.AUTO)
            advanceUntilIdle()
            assertFalse(storage.saved.auto)
            ready = true
            model.resumed()
            advanceUntilIdle()
            assertTrue(storage.saved.auto)
            model.act(Action.UPDATE)
            advanceUntilIdle()
            assertEquals(listOf(release), installs)
            model.act(Action.AUTO)
            advanceUntilIdle()
            assertFalse(storage.saved.auto)
        } finally { store.clear(); work.cancel(); Dispatchers.resetMain() }
    }
}
