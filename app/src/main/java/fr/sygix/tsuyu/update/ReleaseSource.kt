/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class UpdateCause { NETWORK, TIMEOUT, UNREADABLE, LIMITED, WITHDRAWN, SIZE, DIGEST, PACKAGE, VERSION, CERTIFICATE, INSTALLER, SERVICE, PREPARATION, BUSY }

sealed interface Fetch {
    data class Found(val releases: List<Release>) : Fetch
    data class Limited(val retryAt: Long) : Fetch
    data class Failed(val cause: UpdateCause) : Fetch
}

fun interface ReleaseSource {
    fun fetch(): Fetch
}

class GitHubReleases(private val agent: String, private val clock: () -> Long = System::currentTimeMillis) : ReleaseSource {
    override fun fetch(): Fetch {
        val connection = try { URL(URL_RELEASES).openConnection() as HttpsURLConnection } catch (failure: IOException) { return Fetch.Failed(UpdateCause.NETWORK) }
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", agent)
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    try { Fetch.Found(Releases.parse(body)) } catch (unreadable: org.json.JSONException) { Fetch.Failed(UpdateCause.UNREADABLE) }
                }
                403, 429 -> if (status == 429 || connection.getHeaderField("x-ratelimit-remaining") == "0") Fetch.Limited(retryAt(connection)) else Fetch.Failed(UpdateCause.NETWORK)
                else -> Fetch.Failed(UpdateCause.NETWORK)
            }
        } catch (timeout: SocketTimeoutException) { Fetch.Failed(UpdateCause.TIMEOUT) }
        catch (failure: IOException) { Fetch.Failed(UpdateCause.NETWORK) }
        finally { connection.disconnect() }
    }

    private fun retryAt(connection: HttpURLConnection): Long {
        connection.getHeaderField("retry-after")?.toLongOrNull()?.let { return clock() + it * 1000 }
        connection.getHeaderField("x-ratelimit-reset")?.toLongOrNull()?.let { return it * 1000 }
        return clock() + 3_600_000
    }

    companion object {
        const val URL_RELEASES = "https://api.github.com/repos/Sygix/Tsuyu/releases?per_page=30"
    }
}
