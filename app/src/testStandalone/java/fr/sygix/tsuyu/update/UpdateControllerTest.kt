/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
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
        var readFails = false
        var hangs = false
        override suspend fun read(): UpdateSaved {
            if (hangs) kotlinx.coroutines.awaitCancellation()
            if (readFails) throw java.io.IOException("unreadable")
            return saved
        }
        override suspend fun update(transform: (UpdateSaved) -> UpdateSaved) = transform(saved).also { saved = it }
    }
    private val files = object : UpdateFiles {
        var downloadCause: UpdateCause? = null
        var verifyCause: UpdateCause? = null
        var downloads = 0
        override fun file(release: Release) = File(folder.root, "tsuyu-${release.code}.apk")
        var during: () -> Unit = {}
        override fun download(release: Release, target: File): UpdateCause? { downloads++; during(); target.writeBytes(ByteArray(4)); return downloadCause }
        override fun verify(release: Release, file: File, installed: Long) = verifyCause
        override fun clean(keep: File?) { folder.root.listFiles()?.filter { it != keep }?.forEach { it.delete() } }
    }
    private val host = object : UpdateHost {
        override var installed = 100099L
        override val installedVersion get() = if (installed == 100099L) "0.1.0" else "0.2.0"
        var bootCount = 1
        var network = true
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
        override fun online() = network
        override suspend fun running() = live
        var preparedFails = false
        override suspend fun prepared() = if (preparedFails) throw android.os.DeadObjectException() else ready
        override suspend fun idle() = quiet
        var pendingAtCommit: Pending? = Pending(0, "", "", 0, 0)
        override suspend fun install(file: File, release: Release): UpdateCause? { assertTrue(file.exists()); pendingAtCommit = storage.saved.pending; installs += release; return installResult }
        override fun schedule(auto: Boolean) { scheduled = auto }
        override fun recheck(delayMs: Long) { rechecks += delayMs }
    }
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    private val controller = UpdateController(storage, { fetches++; fetched }, files, host, { now }, direct)

    @Test fun replacementOutsideTheUpdaterTurnsAutomaticUpdatingOffBeforeAnyWork() = runTest {
        storage.saved = UpdateSaved(auto = true, observed = 100099, releases = listOf(release.copy(code = 300099, version = "0.3.0")), checkedAt = 0,
            pending = Pending(300099, "0.3.0", File(folder.root, "stale.apk").path, now, 1), result = UpdateController.AVAILABLE, target = 300099, targetVersion = "0.3.0", noticeShown = false)
        File(folder.root, "stale.apk").writeBytes(ByteArray(1))
        host.installed = 200099
        controller.automatic()
        assertFalse(storage.saved.auto)
        assertTrue(storage.saved.releases.isEmpty())
        assertEquals(200099L, storage.saved.observed)
        assertEquals(false, host.scheduled)
        assertEquals(0, fetches)
        assertEquals(0, files.downloads)
        assertTrue(folder.root.listFiles().isNullOrEmpty())
        assertFalse(controller.state.value.auto)
        assertNull(storage.saved.pending)
        assertNull(storage.saved.result)
        assertNull(storage.saved.target)
        assertTrue(storage.saved.noticeShown)
        controller.load()
        assertNull(controller.notice.value)
    }

    @Test fun updaterInstallUnchangedVersionAndFirstObservationKeepTheSetting() = runTest {
        storage.saved = UpdateSaved(auto = true, observed = 100099, target = 200099, targetVersion = "0.2.0", noticeShown = false)
        host.installed = 200099
        controller.load()
        assertTrue(storage.saved.auto)
        assertEquals(200099L, storage.saved.observed)
        assertEquals("0.2.0", controller.notice.value)
        val restarted = UpdateController(storage, { fetches++; fetched }, files, host, { now }, direct)
        restarted.load()
        assertTrue(storage.saved.auto)
        storage.saved = UpdateSaved(auto = true)
        val firstRelease = UpdateController(storage, { fetches++; fetched }, files, host, { now }, direct)
        firstRelease.load()
        assertTrue(storage.saved.auto)
        assertEquals(200099L, storage.saved.observed)
        assertNull(host.scheduled)
    }

    @Test fun timedOutRecordSkipsAutomaticWorkForTheProcess() = runTest {
        storage.saved = UpdateSaved(auto = true, observed = 100099, releases = listOf(release), checkedAt = now)
        storage.hangs = true
        controller.automatic()
        storage.hangs = false
        controller.automatic()
        assertEquals(0, fetches)
        assertEquals(0, files.downloads)
        assertTrue(storage.saved.auto)
    }

    @Test fun unreadableRecordSkipsAutomaticWorkButKeepsManualChecks() = runTest {
        storage.saved = UpdateSaved(auto = true, observed = 100099, releases = listOf(release), checkedAt = now)
        storage.readFails = true
        controller.automatic()
        storage.readFails = false
        controller.automatic()
        controller.check(manual = false)
        assertEquals(0, fetches)
        assertEquals(0, files.downloads)
        controller.check(manual = true)
        assertEquals(1, fetches)
    }

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

    @Test fun serviceDyingDuringPreconditionsIsReportedInsteadOfEscaping() = runTest {
        controller.check(manual = true)
        host.preparedFails = true
        assertEquals(UpdateCause.SERVICE, controller.update())
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

    @Test fun committedSilentUpdateIsNeverRetriedByTheNewVersion() = runTest {
        controller.setAuto(true)
        host.quiet = false
        controller.automatic()
        assertNotNull(storage.saved.pending)
        host.quiet = true
        now += hour
        controller.automatic()
        assertEquals(listOf(release), host.installs)
        assertNull(host.pendingAtCommit)
        storage.saved = storage.saved.copy(pending = Pending(release.code, release.version, files.file(release).apply { writeBytes(ByteArray(4)) }.path, now, 1))
        host.installed = release.code
        val restarted = UpdateController(storage, { fetched }, files, host, { now }, direct)
        restarted.load()
        assertNull(storage.saved.pending)
        restarted.automatic()
        assertEquals(1, host.installs.size)
        assertFalse(files.file(release).exists())
    }

    @Test fun manualUpdateReReadsPreconditionsAfterTheDownload() = runTest {
        controller.check(manual = true)
        files.during = { host.live = false }
        assertEquals(UpdateCause.SERVICE, controller.update())
        assertTrue(host.installs.isEmpty())
        assertFalse(files.file(release).exists())
    }

    @Test fun pendingPrereleaseIsDroppedAfterOptOutAndOfflineAutomaticChecksWait() = runTest {
        val beta = release.copy(tag = "v0.3.0-beta.1", version = "0.3.0-beta.1", code = 300031, prerelease = true)
        fetched = Fetch.Found(listOf(beta))
        controller.setPrereleases(true)
        controller.setAuto(true)
        host.quiet = false
        controller.automatic()
        assertNotNull(storage.saved.pending)
        controller.setPrereleases(false)
        host.quiet = true
        controller.automatic()
        assertTrue(host.installs.isEmpty())
        assertNull(storage.saved.pending)
        host.network = false
        now += 2 * UpdateController.DAY_MS
        val before = fetches
        controller.check(manual = false)
        assertEquals(before, fetches)
        controller.check(manual = true)
        assertEquals(before + 1, fetches)
    }

    @Test fun togglesApplyWhileAnOperationHoldsTheController() = runTest {
        controller.check(manual = true)
        files.during = { kotlinx.coroutines.runBlocking { controller.setAuto(true) } }
        controller.update()
        assertTrue(storage.saved.auto)
        assertTrue(controller.state.value.blocked.not())
    }

    @Test fun anyStandaloneWriteClearsTheStoreCleanupMarker() = runTest {
        val data = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { folder.newFile("updates.preferences_pb") })
        val marker = androidx.datastore.preferences.core.booleanPreferencesKey("store_cleanup_done")
        data.edit { it[marker] = true }
        UpdateStore(data).update { it.copy(observed = 300299) }
        assertNull(data.data.first()[marker])
        assertEquals(300299L, UpdateStore(data).read().observed)
    }
}
