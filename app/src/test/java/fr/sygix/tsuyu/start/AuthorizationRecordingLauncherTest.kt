/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthorizationRecordingLauncherTest {
    @Test fun onlyASuccessfulLaunchRecordsTheAuthorizationAndAFailedRecordKeepsTheLaunch() = runTest {
        var records = 0
        var launchFails = false
        var recordFails = false
        val launcher = AuthorizationRecordingLauncher(object : ServerLauncher {
            override suspend fun launch(progress: (Phase) -> Unit) { if (launchFails) throw StartException(Failure.AUTHENTICATION) }
        }) { records++; check(!recordFails) }
        launcher.launch {}
        assertEquals(1, records)
        launchFails = true
        val failure = runCatching { launcher.launch {} }.exceptionOrNull()
        assertEquals(Failure.AUTHENTICATION, (failure as StartException).failure)
        assertEquals(1, records)
        launchFails = false; recordFails = true
        launcher.launch {}
        assertEquals(2, records)
    }
}
