/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import org.json.JSONArray
import org.json.JSONObject

data class Release(
    val tag: String,
    val version: String,
    val code: Long,
    val prerelease: Boolean,
    val apk: String,
    val size: Long,
    val sha256: String,
    val page: String,
)

object Releases {
    const val ASSET = "tsuyu.apk"
    const val MAX_SIZE = 200L * 1024 * 1024
    private val TAG = Regex("""v(\d{1,2})\.(\d{1,3})\.(\d{1,3})(?:-(alpha|beta|rc)\.?(\d{1,2}))?""")

    fun versionCode(tag: String): Long? {
        val (major, minor, patch, stage, n) = TAG.matchEntire(tag)?.destructured ?: return null
        if (major.toInt() > 20) return null
        val suffix = if (stage.isEmpty()) 99 else {
            if (n.toInt() > 29) return null
            mapOf("alpha" to 0, "beta" to 30, "rc" to 60).getValue(stage) + n.toInt()
        }
        return major.toLong() * 100_000_000 + minor.toLong() * 100_000 + patch.toLong() * 100 + suffix
    }

    fun parse(json: String): List<Release> {
        val items = JSONArray(json)
        return (0 until items.length()).mapNotNull { index -> eligible(items.getJSONObject(index)) }
    }

    private fun eligible(item: JSONObject): Release? {
        if (item.optBoolean("draft", true)) return null
        val tag = item.optString("tag_name")
        val code = versionCode(tag) ?: return null
        val assets = item.optJSONArray("assets") ?: return null
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == ASSET } ?: return null
        val url = asset.optString("browser_download_url")
        val size = asset.optLong("size", -1)
        val digest = asset.optString("digest").removePrefix("sha256:")
        val page = item.optString("html_url")
        if (asset.optString("state") != "uploaded" || size !in 1..MAX_SIZE || !digest.matches(Regex("[a-f0-9]{64}"))) return null
        if (!url.startsWith("https://") || !page.startsWith("https://")) return null
        return Release(tag, tag.removePrefix("v"), code, item.optBoolean("prerelease") || '-' in tag, url, size, digest, page)
    }

    fun offer(releases: List<Release>, installed: Long, prereleases: Boolean): Release? =
        releases.filter { it.code > installed && (prereleases || !it.prerelease) }.maxByOrNull { it.code }

    fun encode(releases: List<Release>): String = JSONArray(releases.map { release ->
        JSONObject().put("tag_name", release.tag).put("draft", false).put("prerelease", release.prerelease).put("html_url", release.page)
            .put("assets", JSONArray().put(JSONObject().put("name", ASSET).put("state", "uploaded").put("size", release.size)
                .put("digest", "sha256:${release.sha256}").put("browser_download_url", release.apk)))
    }).toString()
}
