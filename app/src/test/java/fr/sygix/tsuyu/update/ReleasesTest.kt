/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleasesTest {
    private val sha = "a".repeat(64)
    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false, asset: JSONObject? = asset()) = JSONObject()
        .put("tag_name", tag).put("draft", draft).put("prerelease", prerelease).put("html_url", "https://github.com/Sygix/Tsuyu/releases/tag/$tag")
        .put("assets", JSONArray().apply { asset?.let(::put) })
    private fun asset(name: String = "tsuyu.apk", state: String = "uploaded", size: Long = 5_000_000, digest: String = "sha256:$sha", url: String = "https://github.com/Sygix/Tsuyu/releases/download/x/tsuyu.apk") =
        JSONObject().put("name", name).put("state", state).put("size", size).put("digest", digest).put("browser_download_url", url)

    @Test fun versionCodesFollowTheBuildFormula() {
        assertEquals(200099L, Releases.versionCode("v0.2.0"))
        assertEquals(200061L, Releases.versionCode("v0.2.0-rc.1"))
        assertEquals(200061L, Releases.versionCode("v0.2.0-rc1"))
        assertEquals(100004L, Releases.versionCode("v0.1.0-alpha.4"))
        assertEquals(2_000_000_099L, Releases.versionCode("v20.0.0"))
        assertNull(Releases.versionCode("v21.0.0"))
        assertNull(Releases.versionCode("v0.2.0-rc.30"))
        assertNull(Releases.versionCode("0.2.0"))
        assertNull(Releases.versionCode("v0.2"))
        assertNull(Releases.versionCode("v0.2.0-beta"))
    }

    @Test fun onlyCompleteVerifiableReleasesAreEligible() {
        val json = JSONArray()
            .put(release("v0.3.0"))
            .put(release("v0.4.0", draft = true))
            .put(release("v0.5.0", asset = asset(state = "starter")))
            .put(release("v0.6.0", asset = asset(digest = "")))
            .put(release("v0.7.0", asset = asset(size = Releases.MAX_SIZE + 1)))
            .put(release("v0.8.0", asset = asset(url = "http://example.org/tsuyu.apk")))
            .put(release("v0.9.0", asset = asset(name = "app-release.apk")))
            .put(release("latest"))
            .put(release("v1.0.0", asset = null))
        val releases = Releases.parse(json.toString())
        assertEquals(listOf("v0.3.0"), releases.map { it.tag })
        assertEquals(sha, releases.single().sha256)
        assertEquals("0.3.0", releases.single().version)
    }

    @Test fun offerPrefersHighestCodeAndHonoursPrereleaseOptIn() {
        val releases = Releases.parse(JSONArray().put(release("v0.2.0")).put(release("v0.3.0-beta.1", prerelease = true)).put(release("v0.1.0")).toString())
        assertEquals("v0.2.0", Releases.offer(releases, 100099, prereleases = false)?.tag)
        assertEquals("v0.3.0-beta.1", Releases.offer(releases, 100099, prereleases = true)?.tag)
        assertNull(Releases.offer(releases, 200099, prereleases = false))
        assertTrue(Releases.parse(JSONArray().put(release("v0.3.0-rc.1")).toString()).single().prerelease)
    }

    @Test fun cachedReleasesSurviveEncoding() {
        val releases = Releases.parse(JSONArray().put(release("v0.2.0")).put(release("v0.3.0-rc.2", prerelease = true)).toString())
        assertEquals(releases, Releases.parse(Releases.encode(releases)))
        assertThrows(org.json.JSONException::class.java) { Releases.parse("{oops") }
    }
}
