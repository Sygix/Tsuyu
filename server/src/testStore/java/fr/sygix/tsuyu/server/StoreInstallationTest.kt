/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.ParcelFileDescriptor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreInstallationTest {
    @Test fun storeServerRefusesStrangersAndAnswersTheManagerWithUnsupportedAfterClosingTheFile() {
        var caller = 10100
        val server = ServerControl(10100, { true }, {}, { caller }, { 2000 }, updates = updateInstallation(0))
        val file = java.io.File.createTempFile("tsuyu", ".apk").apply { writeBytes(ByteArray(8)) }
        caller = 10101
        assertThrows(SecurityException::class.java) { server.installUpdate(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 8, "0".repeat(64)) }
        caller = 10100
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val result = server.installUpdate(descriptor, 8, "0".repeat(64))
        assertEquals("UNSUPPORTED", result.getString("status"))
        assertThrows(IllegalStateException::class.java) { descriptor.fd }
    }
}
