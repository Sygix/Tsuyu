/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import fr.sygix.tsuyu.start.BoundedIo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateControllerTest {
    @get:Rule val folder = TemporaryFolder()
    private val hour = 3_600_000L
    private var now = 10 * UpdateController.DAY_MS
    private val release = Release("v0.2.0", "0.2.0", 200099, false, "https://example.org/tsuyu.apk", 4, "b".repeat(64), "https://example.org/v0.2.0")
    private var fetched: Fetch = Fetch.Found(listOf(release))
    private var fetches = 0
    private val storage = object : UpdateStorage {
        var saved = UpdateSaved()
        override suspend fun read() = saved
        override suspend fun update(transform: (UpdateSaved) -> UpdateSaved) = transform(saved).also { saved = it }
    }
    private val files = object : UpdateFiles {
        var downloadCause: UpdateCause? = null
        var verifyCause: UpdateCause? = null
        var downloads = 0
        override fun file(release: Release) = File(folder.root, "tsuyu-${release.code}.apk")
        override fun download(release: Release, target: File): UpdateCause? { downloads++; target.writeBytes(ByteArray(4)); return downloadCause }
        override fun verify(release: Release, file: File, installed: Long) = verifyCause
        override fun clean(keep: File?) { folder.root.listFiles()?.filter { it != keep }?.forEach { it.delete() } }
    }
    private val host = object : UpdateHost {
        override var installed = 100099L
        override val installedVersion get() = if (installed == 100099L) "0.1.0" else "0.2.0"
        var bootCount = 1
        var visible = false
        var live = true
        var ready = true
        var quiet = true
        var installResult: UpdateCause? = null
        val installs = mutableListOf<Release>()
        val rechecks = mutableListOf<Long>()
        var scheduled: Boolean? = null
        override fun boot() = bootCount
        override fun foreground() = visible
        override suspend fun running() = live
        override suspend fun prepared() = ready
        override suspend fun idle() = quiet
        override suspend fun install(file: File, release: Release): UpdateCause? { assertTrue(file.exists()); installs += release; return installResult }
        override fun schedule(auto: Boolean) { scheduled = auto }
        override fun recheck(delayMs: Long) { rechecks += delayMs }
    }
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    private val controller = UpdateController(storage, { fetches++; fetched }, files, host, { now }, direct)

    @Test fun automaticChecksRespectTheDailyAndRetryGatesWhileManualChecksAlwaysRun() = runTest {
        controller.check(manual = false)
        assertEquals(1, fetches)
        assertEquals("0.2.0", controller.state.value.offer?.version)
        now += hour
        controller.check(manual = false)
        assertEquals(1, fetches)
        fetched = Fetch.Limited(now + 30 * hour)
        controller.check(manual = true)
        assertEquals(2, fetches)
        now += 25 * hour
        controller.check(manual = false)
        assertEquals(2, fetches)
        now += 6 * hour
        fetched = Fetch.Found(listOf(release))
        controller.check(manual = false)
        assertEquals(3, fetches)
        assertTrue(UpdateController.due(UpdateSaved(checkedAt = now + hour), now))
    }

    @Test fun failedCheckKeepsTheKnownOfferAndRecordsItsCause() = runTest {
        controller.check(manual = true)
        fetched = Fetch.Failed(UpdateCause.NETWORK)
        controller.check(manual = true)
        assertEquals("0.2.0", controller.state.value.offer?.version)
        assertEquals("NETWORK", controller.state.value.result)
    }

    @Test fun prereleasesAreOfferedOnlyAfterOptIn() = runTest {
        val beta = release.copy(tag = "v0.3.0-beta.1", version = "0.3.0-beta.1", code = 300031, prerelease = true)
        fetched = Fetch.Found(listOf(release, beta))
        controller.check(manual = true)
        assertEquals("0.2.0", controller.state.value.offer?.version)
        controller.setPrereleases(true)
        assertEquals(1, fetches)
        assertEquals("0.3.0-beta.1", controller.state.value.offer?.version)
    }

    @Test fun manualUpdateNeedsARunningPreparedServiceBeforeDownloading() = runTest {
        controller.check(manual = true)
        host.live = false
        assertEquals(UpdateCause.SERVICE, controller.update())
        host.live = true
        host.ready = false
        assertEquals(UpdateCause.PREPARATION, controller.update())
        assertEquals(0, files.downloads)
        assertTrue(host.installs.isEmpty())
    }

    @Test fun withdrawnOrNonConformingReleaseIsNeverInstalled() = runTest {
        controller.check(manual = true)
        fetched = Fetch.Found(emptyList())
        assertEquals(UpdateCause.WITHDRAWN, controller.update())
        assertEquals(0, files.downloads)
        fetched = Fetch.Found(listOf(release))
        controller.check(manual = true)
        files.verifyCause = UpdateCause.DIGEST
        assertEquals(UpdateCause.DIGEST, controller.update())
        assertFalse(files.file(release).exists())
        assertEquals("DIGEST", controller.state.value.result)
        assertTrue(host.installs.isEmpty())
    }

    @Test fun successfulInstallShowsTheNoticeOnceInTheNewVersion() = runTest {
        controller.check(manual = true)
        assertNull(controller.update())
        assertEquals(listOf(release), host.installs)
        assertFalse(files.file(release).exists())
        host.installed = 200099
        val restarted = UpdateController(storage, { fetched }, files, host, { now }, direct)
        restarted.load()
        assertEquals("0.2.0", restarted.notice.value)
        assertNull(restarted.state.value.offer)
        restarted.consumeNotice()
        assertNull(restarted.notice.value)
        val again = UpdateController(storage, { fetched }, files, host, { now }, direct)
        again.load()
        assertNull(again.notice.value)
    }

    @Test fun failedInstallLeavesNoNoticeAndRecordsTheInstallerCause() = runTest {
        controller.check(manual = true)
        host.installResult = UpdateCause.INSTALLER
        assertEquals(UpdateCause.INSTALLER, controller.update())
        assertEquals("INSTALLER", storage.saved.result)
        assertNull(storage.saved.target)
    }

    @Test fun silentUpdateWaitsForIdleThenDropsTheFileAfterItsWindow() = runTest {
        controller.setAuto(true)
        assertEquals(true, host.scheduled)
        host.quiet = false
        controller.automatic()
        assertTrue(host.installs.isEmpty())
        assertEquals(listOf(UpdateController.IDLE_RECHECK_MS), host.rechecks)
        assertEquals(UpdatePhase.WAITING, controller.state.value.phase)
        assertTrue(files.file(release).exists())
        now += 2 * hour
        host.visible = true
        host.quiet = true
        controller.automatic()
        assertTrue(host.installs.isEmpty())
        assertEquals(1, files.downloads)
        now += UpdateController.DAY_MS
        host.visible = false
        controller.automatic()
        assertTrue(host.installs.isEmpty())
        assertNull(storage.saved.pending)
        assertFalse(files.file(release).exists())
    }

    @Test fun silentUpdateInstallsWhenIdleAndDoesNothingWhileDisabled() = runTest {
        controller.automatic()
        assertEquals(0, fetches)
        controller.setAuto(true)
        controller.automatic()
        assertEquals(listOf(release), host.installs)
        controller.setAuto(false)
        assertEquals(false, host.scheduled)
    }

    @Test fun silentUpdateRecordsALostPrerequisiteWithoutDownloading() = runTest {
        controller.setAuto(true)
        host.ready = false
        controller.automatic()
        assertEquals(0, files.downloads)
        assertEquals("PREPARATION", storage.saved.result)
    }

    @Test fun pendingFileIsDiscardedAfterARebootAndUnknownFilesAreCleaned() = runTest {
        controller.setAuto(true)
        host.quiet = false
        controller.automatic()
        val stray = File(folder.root, "partial.apk").apply { writeBytes(ByteArray(1)) }
        host.bootCount = 2
        controller.load()
        assertNull(storage.saved.pending)
        assertFalse(files.file(release).exists())
        assertFalse(stray.exists())
    }
}
