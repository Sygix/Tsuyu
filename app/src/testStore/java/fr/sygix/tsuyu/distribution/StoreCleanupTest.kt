/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.distribution

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreCleanupTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun cleanupCancelsUpdaterWorkClearsStateAndManagedFilesOnlyOnce() = runTest {
        val data = PreferenceDataStoreFactory.create(corruptionHandler = updateCorruption, scope = backgroundScope, produceFile = { folder.newFile("updates.preferences_pb") })
        data.edit { it[stringPreferencesKey("state")] = """{"auto":true,"observed":300299,"pending":{"path":"/elsewhere/keep.apk"}}""" }
        val cache = folder.newFolder("cache")
        val managed = File(cache, "update").apply { mkdirs() }
        val apk = File(managed, "tsuyu-300099.apk").apply { writeBytes(ByteArray(4)) }
        val outside = folder.newFile("outside.apk")
        Files.createSymbolicLink(File(managed, "link.apk").toPath(), outside.toPath())
        val cancelled = mutableListOf<String>()
        val cleanup = StoreCleanup(data, cache) { cancelled += it }
        assertTrue(cleanup.run().isSuccess)
        assertEquals(listOf("tsuyu-update", "tsuyu-update-recheck"), cancelled)
        assertFalse(apk.exists())
        assertTrue(outside.exists())
        assertEquals("""{"observed":300299}""", data.data.first()[stringPreferencesKey("state")])
        assertTrue(cleanup.run().isSuccess)
        assertEquals(2, cancelled.size)
    }

    @Test fun failedCancellationIsRetriedAtTheNextStartAndCorruptStateIsReplaced() = runTest {
        val file = folder.newFile("broken.preferences_pb").apply { writeBytes(byteArrayOf(9, 9, 9, 9)) }
        val data = PreferenceDataStoreFactory.create(corruptionHandler = updateCorruption, scope = backgroundScope, produceFile = { file })
        var fails = true
        val cancelled = mutableListOf<String>()
        val cleanup = StoreCleanup(data, folder.newFolder("cache")) { name -> check(!fails) { "WorkManager unavailable" }; cancelled += name }
        assertTrue(cleanup.run().isFailure)
        fails = false
        assertTrue(cleanup.run().isSuccess)
        assertEquals(listOf("tsuyu-update", "tsuyu-update-recheck"), cancelled)
    }

    @Test fun aCleanupThatTimesOutIsNotRecordedAndRunsAgain() = runTest {
        val data = PreferenceDataStoreFactory.create(corruptionHandler = updateCorruption, scope = backgroundScope, produceFile = { folder.newFile("slow.preferences_pb") })
        var hang = true
        val cancelled = mutableListOf<String>()
        val cleanup = StoreCleanup(data, folder.newFolder("cache")) { name -> if (hang) kotlinx.coroutines.awaitCancellation(); cancelled += name }
        assertTrue(cleanup.run().exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)
        hang = false
        assertTrue(cleanup.run().isSuccess)
        assertEquals(listOf("tsuyu-update", "tsuyu-update-recheck"), cancelled)
    }
}
