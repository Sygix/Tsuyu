/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageWatchTest {
    @Test fun replacementAndRemovalExitButProcessRestartDoesNot() {
        val original = PackageStamp("base.apk", 1, 10100)
        for (replacement in listOf(null, original.copy(path = "new.apk"), original.copy(updated = 2), original.copy(uid = 10101))) {
            var exits = 0
            var current: PackageStamp? = original
            val watch = PackageWatch(original, { current }, { exits++ })
            assertTrue(watch.reconcile())
            assertEquals(0, exits)
            current = replacement
            assertFalse(watch.reconcile())
            assertEquals(1, exits)
        }
    }
    @Test fun replacementBetweenLaunchLookupAndBaselineCannotWatchTheNewApk() {
        val launched = PackageStamp("old.apk", 1, 10100)
        for (installed in listOf(launched.copy(path = "new.apk", updated = 2), launched.copy(uid = 10101))) {
            assertThrows(IllegalStateException::class.java) {
                PackageWatch(installed, { installed }, {}, launched.path, launched.uid)
            }
        }
    }
}
