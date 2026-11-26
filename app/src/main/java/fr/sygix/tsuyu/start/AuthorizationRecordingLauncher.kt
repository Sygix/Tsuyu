/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class AuthorizationRecordingLauncher(
    private val launcher: ServerLauncher,
    private val authenticated: suspend () -> Unit,
) : ServerLauncher {
    override suspend fun launch(progress: (Phase) -> Unit) {
        launcher.launch(progress)
        try { withTimeout(5000) { authenticated() } }
        catch (expired: TimeoutCancellationException) { Log.e("Tsuyu", "Authorization status not saved", expired) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Log.e("Tsuyu", "Authorization status not saved", failure) }
    }
}
